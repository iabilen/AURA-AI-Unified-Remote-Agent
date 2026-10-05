import { createServer, type IncomingMessage } from "node:http";
import { randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { DurableEventStore } from "./event-store.js";
import { DeviceRegistry } from "./device-registry.js";
import { RelayDatabase } from "./relay-database.js";
import { WebSocketServer, WebSocket } from "ws";
import { createMcpHandler, McpServer } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import * as z from "zod/v4";

type JsonRecord = Record<string, unknown>;
type Device = { id: string; socket: WebSocket; connectedAt: number; lastSeenAt: number };
type PendingTask = { resolve: (value: JsonRecord) => void; timer: NodeJS.Timeout; deviceId: string };

const PORT = Number(process.env.PORT ?? 8787);
const MCP_TOKEN = process.env.AURA_MCP_TOKEN ?? "";
const CONFIGURED_DEVICE_TOKENS = loadDeviceTokens();
const EVENT_LIMIT = 200;
const EVENT_STORE_PATH = process.env.AURA_EVENT_STORE_PATH ?? "./data/events.json";
const DEVICE_REGISTRY_PATH = process.env.AURA_DEVICE_REGISTRY_PATH ?? "./data/devices.json";
const RELAY_DATABASE_URL = process.env.AURA_RELAY_DATABASE_URL ?? process.env.DATABASE_URL ?? "";
const TASK_TIMEOUT_MS = 60_000;
const PAIRING_TTL_MS = 5 * 60_000;

const pairingCodes = new Map<string, { expiresAt: number }>();
setInterval(() => { const now = Date.now(); for (const [code, entry] of pairingCodes) if (entry.expiresAt <= now) pairingCodes.delete(code); }, PAIRING_TTL_MS).unref();
function newPairingCode(): string {
  const code = randomBytes(5).toString("base64url").slice(0, 8).toUpperCase();
  pairingCodes.set(code, { expiresAt: Date.now() + PAIRING_TTL_MS });
  return code;
}
function consumePairingCode(code: string): boolean {
  const entry = pairingCodes.get(code); pairingCodes.delete(code);
  return !!entry && entry.expiresAt > Date.now();
}
function issueDeviceToken(): string { return randomBytes(32).toString("base64url"); }

if (MCP_TOKEN.length < 24) throw new Error("AURA_MCP_TOKEN must be at least 24 characters");
if (Object.keys(CONFIGURED_DEVICE_TOKENS).length === 0) throw new Error("AURA_DEVICE_TOKENS must contain at least one device token");

const deviceRegistry = new DeviceRegistry(DEVICE_REGISTRY_PATH, CONFIGURED_DEVICE_TOKENS);
const devices = new Map<string, Device>();
const eventStore = new DurableEventStore(EVENT_STORE_PATH, EVENT_LIMIT);
const relayDatabase = new RelayDatabase(RELAY_DATABASE_URL);
await relayDatabase.init();
const pendingTasks = new Map<string, PendingTask>();

function loadDeviceTokens(): Record<string, string> {
  try {
    const value: unknown = JSON.parse(process.env.AURA_DEVICE_TOKENS ?? "{}");
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    const result: Record<string, string> = {};
    for (const [id, token] of Object.entries(value as Record<string, unknown>)) {
      if (typeof token === "string" && token.length >= 24) result[id] = token;
    }
    return result;
  } catch {
    throw new Error("AURA_DEVICE_TOKENS must be valid JSON");
  }
}

function bearer(req: { headers: Record<string, string | string[] | undefined> }): string {
  const raw = req.headers.authorization;
  return typeof raw === "string" && raw.startsWith("Bearer ") ? raw.slice(7) : "";
}

async function rememberEvent(event: JsonRecord, deviceId: string): Promise<string> {
  const id = typeof event.id === "string" ? event.id : randomUUID();
  const receivedAt = Date.now();
  eventStore.remember({ ...event, id, deviceId, receivedAt });
  await relayDatabase.recordEvent(event, deviceId, id, receivedAt);
  return id;
}

function send(ws: WebSocket, message: JsonRecord): boolean {
  if (ws.readyState !== WebSocket.OPEN) return false;
  ws.send(JSON.stringify(message));
  return true;
}

async function resultForTask(message: JsonRecord) {
  const id = typeof message.id === "string" ? message.id : "";
  const pending = pendingTasks.get(id);
  if (!pending) return;
  clearTimeout(pending.timer);
  pendingTasks.delete(id);
  const result = message.payload ?? message.result ?? null;
  await relayDatabase.completeTask(id, "completed", result && typeof result === "object" ? result as JsonRecord : null, Date.now());
  pending.resolve({ id, status: "completed", result });
}

function connectDevice(id: string, socket: WebSocket) {
  const old = devices.get(id);
  if (old?.socket.readyState === WebSocket.OPEN) old.socket.close(4001, "replaced");
  const now = Date.now();
  devices.set(id, { id, socket, connectedAt: now, lastSeenAt: now });
  void relayDatabase.recordDevice(id, true, now, now);
}

function buildMcpServer(): McpServer {
  const server = new McpServer({ name: "aura-relay", version: "0.1.0" });

  server.registerTool("aura_devices", {
    description: "List authorized AURA devices and their current connection state.",
    inputSchema: z.object({}),
  }, async () => ({ content: [{ type: "text", text: JSON.stringify({
    configured: deviceRegistry.configuredIds(),
    revoked: deviceRegistry.configuredIds().filter((id) => deviceRegistry.isRevoked(id)),
    connected: [...devices.values()].map((device) => ({ deviceId: device.id, connected: device.socket.readyState === WebSocket.OPEN, connectedAt: device.connectedAt, lastSeenAt: device.lastSeenAt })),
  }) }] }));

  server.registerTool("aura_revoke_device", {
    description: "Revoke a configured AURA device so it can no longer establish a bridge connection until restored.",
    inputSchema: z.object({ deviceId: z.string().min(1) }),
  }, async ({ deviceId }) => {
    if (!deviceRegistry.revoke(deviceId)) {
      return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "not_configured" }) }], isError: true };
    }
    const device = devices.get(deviceId);
    if (device?.socket.readyState === WebSocket.OPEN) device.socket.close(4003, "revoked");
    devices.delete(deviceId);
    return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "revoked" }) }] };
  });

  server.registerTool("aura_restore_device", {
    description: "Restore a previously revoked configured AURA device.",
    inputSchema: z.object({ deviceId: z.string().min(1) }),
  }, async ({ deviceId }) => {
    if (!deviceRegistry.restore(deviceId)) {
      return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "not_revoked_or_not_configured" }) }], isError: true };
    }
    return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "restored" }) }] };
  });

  server.registerTool("aura_create_pairing_code", {
    description: "Create a short-lived one-time AURA device enrollment code.",
    inputSchema: z.object({}),
  }, async () => ({ content: [{ type: "text", text: JSON.stringify({ code: newPairingCode(), expiresInSeconds: PAIRING_TTL_MS / 1000 }) }] }));

  server.registerTool("aura_rotate_device_credential", {
    description: "Rotate the credential for an enrolled AURA device; the new token is returned once.",
    inputSchema: z.object({ deviceId: z.string().min(1) }),
  }, async ({ deviceId }) => {
    const token = issueDeviceToken();
    if (!deviceRegistry.rotate(deviceId, token)) return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "not_rotatable" }) }], isError: true };
    const device = devices.get(deviceId);
    if (device?.socket.readyState === WebSocket.OPEN) {
      send(device.socket, { type: "credential_rotated", protocol: 1, deviceId, token });
      setTimeout(() => { if (device.socket.readyState === WebSocket.OPEN) device.socket.close(4004, "credential rotated"); }, 100);
    }
    devices.delete(deviceId);
    return { content: [{ type: "text", text: JSON.stringify({ deviceId, status: "rotated", token }) }] };
  });

  server.registerTool("aura_status", {
    description: "Get the status of one connected AURA device.",
    inputSchema: z.object({ deviceId: z.string().min(1) }),
  }, async ({ deviceId }) => {
    const device = devices.get(deviceId);
    if (!device || device.socket.readyState !== WebSocket.OPEN) return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: false }) }] };
    const requestId = randomUUID();
    if (!send(device.socket, { type: "status_request", id: requestId, protocol: 1 })) return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: false }) }] };
    return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: true, requestId }) }] };
  });

  server.registerTool("aura_send_task", {
    description: "Send an authorized task to an AURA Android device. High-impact actions remain subject to AURA confirmation/safety policy.",
    inputSchema: z.object({ deviceId: z.string().min(1), task: z.string().min(1).max(8000) }),
  }, async ({ deviceId, task }) => {
    const device = devices.get(deviceId);
    if (!device || device.socket.readyState !== WebSocket.OPEN) return { content: [{ type: "text", text: JSON.stringify({ status: "offline", deviceId }) }], isError: true };
    const id = randomUUID();
    await relayDatabase.recordTask(id, deviceId, "mcp", task, Date.now());
    const result = await new Promise<JsonRecord>((resolve) => {
      const timer = setTimeout(() => {
        pendingTasks.delete(id);
        void relayDatabase.completeTask(id, "timeout", { deviceId }, Date.now());
        resolve({ id, status: "timeout", deviceId });
      }, TASK_TIMEOUT_MS);
      pendingTasks.set(id, { resolve, timer, deviceId });
      if (!send(device.socket, { type: "task", id, source: "mcp", priority: 10, task, protocol: 1 })) {
        clearTimeout(timer); pendingTasks.delete(id);
        void relayDatabase.completeTask(id, "offline", { deviceId }, Date.now());
        resolve({ id, status: "offline", deviceId });
      }
    });
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  });

  server.registerTool("aura_events", {
    description: "Read recent Android events received by AURA, including notifications.",
    inputSchema: z.object({ deviceId: z.string().optional(), limit: z.number().int().min(1).max(50).default(20) }),
  }, async ({ deviceId, limit }) => ({
    content: [{ type: "text", text: JSON.stringify(relayDatabase.enabled ? await relayDatabase.listEvents(deviceId, limit) : eventStore.list(deviceId, limit)) }],
  }));

  return server;
}

const mcpHandler = createMcpHandler(buildMcpServer);
const nodeMcpHandler = toNodeHandler(mcpHandler);
const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://aura.local");
  if (url.pathname === "/healthz") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, devices: devices.size, events: eventStore.count(), database: relayDatabase.enabled }));
    return;
  }
  if (url.pathname === "/enroll" && req.method === "POST") {
    let body = ""; req.on("data", chunk => { body += chunk.toString(); if (body.length > 2048) req.destroy(); });
    req.on("end", () => {
      try {
        const input = JSON.parse(body) as { pairingCode?: string; deviceId?: string };
        const code = input.pairingCode?.trim().toUpperCase() ?? "";
        const deviceId = input.deviceId?.trim() ?? "";
        if (!deviceId || deviceId.length > 128 || !consumePairingCode(code)) { res.writeHead(401, { "content-type": "application/json" }); res.end(JSON.stringify({ error: "invalid_pairing" })); return; }
        const token = issueDeviceToken();
        if (!deviceRegistry.enroll(deviceId, token)) { res.writeHead(409, { "content-type": "application/json" }); res.end(JSON.stringify({ error: "device_already_enrolled" })); return; }
        res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
        res.end(JSON.stringify({ deviceId, token }));
      } catch { res.writeHead(400); res.end("bad request"); }
    });
    return;
  }

  if (url.pathname !== "/mcp") { res.writeHead(404); res.end("not found"); return; }
  const mcpBearer = Buffer.from(bearer(req));
  const mcpExpected = Buffer.from(MCP_TOKEN);
  if (mcpBearer.length !== mcpExpected.length || !timingSafeEqual(mcpBearer, mcpExpected)) {
    res.writeHead(401, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "unauthorized" }));
    return;
  }
  await nodeMcpHandler(req, res);
});

const wss = new WebSocketServer({ noServer: true });
server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url ?? "/", "http://aura.local");
  if (url.pathname !== "/device") { socket.destroy(); return; }
  const deviceId = req.headers["x-aura-device"];
  if (typeof deviceId !== "string" || !deviceRegistry.isAuthorized(deviceId, bearer(req))) {
    socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n"); socket.destroy(); return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req, deviceId));
});

wss.on("connection", (ws: WebSocket, _req: IncomingMessage, deviceId: string) => {
  connectDevice(deviceId, ws);
  send(ws, { type: "hello", protocol: 1, deviceId, capabilities: { task: true, event: true, status: true } });
  ws.on("message", async (raw) => {
    let message: JsonRecord;
    try { message = JSON.parse(raw.toString()) as JsonRecord; } catch { return; }
    const device = devices.get(deviceId);
    if (device) device.lastSeenAt = Date.now();
    if (message.type === "event" || message.type === "result") {
      const id = await rememberEvent(message, deviceId);
      send(ws, { type: "ack", id, protocol: 1 });
      if (message.type === "result") await resultForTask(message);
      return;
    }
    if (message.type === "status") { await rememberEvent(message, deviceId); return; }
    if (message.type === "ping") send(ws, { type: "pong", id: message.id ?? null, protocol: 1 });
  });
  ws.on("close", () => {
    if (devices.get(deviceId)?.socket === ws) {
      devices.delete(deviceId);
      const now = Date.now();
      void relayDatabase.recordDevice(deviceId, false, now, now);
      for (const [taskId, pending] of pendingTasks) {
        if (pending.deviceId !== deviceId) continue;
        clearTimeout(pending.timer);
        pendingTasks.delete(taskId);
        void relayDatabase.completeTask(taskId, "offline", { deviceId }, now);
        pending.resolve({ id: taskId, status: "offline", deviceId });
      }
    }
  });
  ws.on("error", () => ws.close());
});

server.listen(PORT, () => {
  console.log(`AURA relay listening on ${PORT}`);
  console.log("MCP endpoint: /mcp");
  console.log("Device WebSocket endpoint: /device");
});

process.on("SIGTERM", () => { void relayDatabase.close(); });
process.on("SIGINT", () => { void relayDatabase.close(); });
