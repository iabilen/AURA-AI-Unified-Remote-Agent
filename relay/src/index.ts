import { createServer, type IncomingMessage } from "node:http";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { createMcpHandler, McpServer } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import * as z from "zod/v4";

type JsonRecord = Record<string, unknown>;
type Device = { id: string; socket: WebSocket; connectedAt: number; lastSeenAt: number };
type PendingTask = { resolve: (value: JsonRecord) => void; timer: NodeJS.Timeout };

const PORT = Number(process.env.PORT ?? 8787);
const MCP_TOKEN = process.env.AURA_MCP_TOKEN ?? "";
const DEVICE_TOKENS = loadDeviceTokens();
const EVENT_LIMIT = 200;
const TASK_TIMEOUT_MS = 60_000;

if (MCP_TOKEN.length < 24) throw new Error("AURA_MCP_TOKEN must be at least 24 characters");
if (Object.keys(DEVICE_TOKENS).length === 0) throw new Error("AURA_DEVICE_TOKENS must contain at least one device token");

const devices = new Map<string, Device>();
const events: JsonRecord[] = [];
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

function safeEqual(a: string, b: string): boolean {
  const aa = Buffer.from(a);
  const bb = Buffer.from(b);
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}

function bearer(req: { headers: Record<string, string | string[] | undefined> }): string {
  const raw = req.headers.authorization;
  return typeof raw === "string" && raw.startsWith("Bearer ") ? raw.slice(7) : "";
}

function deviceTokenFor(id: string): string | undefined { return DEVICE_TOKENS[id] ?? DEVICE_TOKENS["*"]; }

function rememberEvent(event: JsonRecord, deviceId: string): string {
  const id = typeof event.id === "string" ? event.id : randomUUID();
  if (events.some((item) => item.id === id && item.deviceId === deviceId)) return id;
  events.push({ ...event, id, deviceId, receivedAt: Date.now() });
  while (events.length > EVENT_LIMIT) events.shift();
  return id;
}

function send(ws: WebSocket, message: JsonRecord): boolean {
  if (ws.readyState !== WebSocket.OPEN) return false;
  ws.send(JSON.stringify(message));
  return true;
}

function resultForTask(message: JsonRecord) {
  const id = typeof message.id === "string" ? message.id : "";
  const pending = pendingTasks.get(id);
  if (!pending) return;
  clearTimeout(pending.timer);
  pendingTasks.delete(id);
  pending.resolve({ id, status: "completed", result: message.payload ?? message.result ?? null });
}

function connectDevice(id: string, socket: WebSocket) {
  const old = devices.get(id);
  if (old?.socket.readyState === WebSocket.OPEN) old.socket.close(4001, "replaced");
  const now = Date.now();
  devices.set(id, { id, socket, connectedAt: now, lastSeenAt: now });
}

function buildMcpServer(): McpServer {
  const server = new McpServer({ name: "aura-relay", version: "0.1.0", description: "Secure relay between ChatGPT MCP and AURA Android agents." });

  server.registerTool("aura_devices", {
    description: "List authorized AURA devices and their current connection state.",
    inputSchema: z.object({}),
  }, async () => ({
    content: [{ type: "text", text: JSON.stringify({
      configured: Object.keys(DEVICE_TOKENS).filter((id) => id !== "*"),
      connected: [...devices.values()].map((device) => ({ deviceId: device.id, connected: device.socket.readyState === WebSocket.OPEN, connectedAt: device.connectedAt, lastSeenAt: device.lastSeenAt })),
    }) }],
  }));

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
    const result = await new Promise<JsonRecord>((resolve) => {
      const timer = setTimeout(() => { pendingTasks.delete(id); resolve({ id, status: "timeout", deviceId }); }, TASK_TIMEOUT_MS);
      pendingTasks.set(id, { resolve, timer });
      if (!send(device.socket, { type: "task", id, source: "mcp", priority: 10, task, protocol: 1 })) {
        clearTimeout(timer); pendingTasks.delete(id); resolve({ id, status: "offline", deviceId });
      }
    });
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  });

  server.registerTool("aura_events", {
    description: "Read recent Android events received by AURA, including notifications.",
    inputSchema: z.object({ deviceId: z.string().optional(), limit: z.number().int().min(1).max(50).default(20) }),
  }, async ({ deviceId, limit }) => ({
    content: [{ type: "text", text: JSON.stringify(events.filter((event) => !deviceId || event.deviceId === deviceId).slice(-limit)) }],
  }));

  return server;
}

const mcpHandler = createMcpHandler(buildMcpServer);
const nodeMcpHandler = toNodeHandler(mcpHandler);
const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://aura.local");
  if (url.pathname === "/healthz") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, devices: devices.size, events: events.length }));
    return;
  }
  if (url.pathname !== "/mcp") { res.writeHead(404); res.end("not found"); return; }
  if (!safeEqual(bearer(req), MCP_TOKEN)) {
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
  if (typeof deviceId !== "string" || !safeEqual(bearer(req), deviceTokenFor(deviceId) ?? "")) {
    socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n"); socket.destroy(); return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req, deviceId));
});

wss.on("connection", (ws: WebSocket, _req: IncomingMessage, deviceId: string) => {
  connectDevice(deviceId, ws);
  send(ws, { type: "hello", protocol: 1, deviceId, capabilities: { task: true, event: true, status: true } });
  ws.on("message", (raw) => {
    let message: JsonRecord;
    try { message = JSON.parse(raw.toString()) as JsonRecord; } catch { return; }
    const device = devices.get(deviceId);
    if (device) device.lastSeenAt = Date.now();
    if (message.type === "event" || message.type === "result") {
      const id = rememberEvent(message, deviceId);
      send(ws, { type: "ack", id, protocol: 1 });
      if (message.type === "result") resultForTask(message);
      return;
    }
    if (message.type === "status") { rememberEvent(message, deviceId); return; }
    if (message.type === "ping") send(ws, { type: "pong", id: message.id ?? null, protocol: 1 });
  });
  ws.on("close", () => { if (devices.get(deviceId)?.socket === ws) devices.delete(deviceId); });
  ws.on("error", () => ws.close());
});

server.listen(PORT, () => {
  console.log(`AURA relay listening on ${PORT}`);
  console.log("MCP endpoint: /mcp");
  console.log("Device WebSocket endpoint: /device");
});
