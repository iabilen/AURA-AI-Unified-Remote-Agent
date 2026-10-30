import { createServer, type IncomingMessage } from "node:http";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { DurableEventJournal } from "./durable-event-journal.js";
import { DurableTaskStore } from "./durable-task-store.js";
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
const EVENT_STORE_PATH = process.env.AURA_EVENT_STORE_PATH ?? "./data/events.json";
const TASK_STORE_PATH = process.env.AURA_TASK_STORE_PATH ?? "./data/tasks.json";
const TASK_TIMEOUT_MS = 60_000;

if (MCP_TOKEN.length < 24) throw new Error("AURA_MCP_TOKEN must be at least 24 characters");
if (Object.keys(DEVICE_TOKENS).length === 0) throw new Error("AURA_DEVICE_TOKENS must contain at least one device token");

const devices = new Map<string, Device>();
const eventJournal = new DurableEventJournal(EVENT_STORE_PATH);
const taskStore = new DurableTaskStore(TASK_STORE_PATH);
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
  } catch { throw new Error("AURA_DEVICE_TOKENS must be valid JSON"); }
}

function safeEqual(a: string, b: string): boolean {
  const aa = Buffer.from(a), bb = Buffer.from(b);
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
function bearer(req: { headers: Record<string, string | string[] | undefined> }): string {
  const raw = req.headers.authorization;
  return typeof raw === "string" && raw.startsWith("Bearer ") ? raw.slice(7) : "";
}
function deviceTokenFor(id: string): string | undefined { return DEVICE_TOKENS[id] ?? DEVICE_TOKENS["*"]; }

function rememberEvent(event: JsonRecord, deviceId: string): string {
  const id = typeof event.id === "string" && event.id.trim() ? event.id : randomUUID();
  return eventJournal.accept({ ...event, id }, deviceId).id;
}
function send(ws: WebSocket, message: JsonRecord): boolean {
  if (ws.readyState !== WebSocket.OPEN) return false;
  ws.send(JSON.stringify(message));
  return true;
}
function finishTask(message: JsonRecord): void {
  const id = typeof message.id === "string" ? message.id : "";
  const task = taskStore.get(id);
  if (!task || ["completed","failed","cancelled","expired"].includes(task.status)) return;
  const status = message.status === "failed" ? "failed" : "completed";
  const result = message.payload ?? message.result ?? null;
  taskStore.transition(id, status, status === "failed" ? { error: JSON.stringify(result) } : { result });
  const pending = pendingTasks.get(id);
  if (pending) {
    clearTimeout(pending.timer);
    pendingTasks.delete(id);
    pending.resolve({ id, status, result });
  }
}
function connectDevice(id: string, socket: WebSocket): void {
  const old = devices.get(id);
  if (old?.socket.readyState === WebSocket.OPEN) old.socket.close(4001, "replaced");
  const now = Date.now();
  devices.set(id, { id, socket, connectedAt: now, lastSeenAt: now });
}

function buildMcpServer(): McpServer {
  const server = new McpServer({ name: "aura-relay", version: "0.2.0" });
  server.registerTool("aura_devices", {
    description: "List authorized AURA devices and connection state.",
    inputSchema: {},
  }, async () => ({ content: [{ type: "text", text: JSON.stringify({
    configured: Object.keys(DEVICE_TOKENS).filter(id => id !== "*"),
    connected: [...devices.values()].map(d => ({ deviceId: d.id, connected: d.socket.readyState === WebSocket.OPEN, connectedAt: d.connectedAt, lastSeenAt: d.lastSeenAt })),
  }) }] }));

  server.registerTool("aura_status", {
    description: "Get the status of one connected AURA device.",
    inputSchema: { deviceId: z.string().min(1) },
  }, async ({ deviceId }) => {
    const device = devices.get(deviceId);
    if (!device || device.socket.readyState !== WebSocket.OPEN) return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: false }) }] };
    const requestId = randomUUID();
    if (!send(device.socket, { type: "status_request", id: requestId, protocol: 1 })) return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: false }) }] };
    return { content: [{ type: "text", text: JSON.stringify({ deviceId, connected: true, requestId }) }] };
  });

  server.registerTool("aura_tasks", {
    description: "Inspect durable AURA task states, including tasks recovered after Relay restart.",
    inputSchema: { limit: z.number().int().min(1).max(100).default(25) },
  }, async ({ limit }) => ({ content: [{ type: "text", text: JSON.stringify(taskStore.list(limit)) }] }));

  server.registerTool("aura_send_task", {
    description: "Send an authorized task to an AURA Android device and wait for its correlated result. Task acceptance is durable before dispatch.",
    inputSchema: { deviceId: z.string().min(1), task: z.string().min(1).max(8000), idempotencyKey: z.string().min(1).max(200).optional() },
  }, async ({ deviceId, task, idempotencyKey }) => {
    const device = devices.get(deviceId);
    if (!device || device.socket.readyState !== WebSocket.OPEN) return { content: [{ type: "text", text: JSON.stringify({ status: "offline", deviceId }) }], isError: true };
    const id = randomUUID();
    const durable = taskStore.create({ id, deviceId, task, idempotencyKey: idempotencyKey ?? id, expiresAt: Date.now() + TASK_TIMEOUT_MS });
    if (durable.id !== id) return { content: [{ type: "text", text: JSON.stringify({ id: durable.id, status: durable.status, duplicate: true }) }] };
    taskStore.transition(id, "dispatched");
    const result = await new Promise<JsonRecord>(resolve => {
      const timer = setTimeout(() => {
        pendingTasks.delete(id);
        const current = taskStore.get(id);
        if (current && !["completed","failed","cancelled","expired"].includes(current.status)) taskStore.transition(id, "expired");
        resolve({ id, status: "timeout", deviceId });
      }, TASK_TIMEOUT_MS);
      pendingTasks.set(id, { resolve, timer });
      if (!send(device.socket, { type: "task", id, source: "mcp", priority: 10, task, protocol: 1 })) {
        clearTimeout(timer);
        pendingTasks.delete(id);
        taskStore.transition(id, "failed", { error: "Device disconnected before dispatch" });
        resolve({ id, status: "offline", deviceId });
      } else taskStore.transition(id, "awaiting_device");
    });
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  });
  server.registerTool("aura_events", {
    description: "Read recent persisted Android events received by AURA.",
    inputSchema: { deviceId: z.string().optional(), limit: z.number().int().min(1).max(50).default(20) },
  }, async ({ deviceId, limit }) => ({ content: [{ type: "text", text: JSON.stringify(eventJournal.list(deviceId, limit)) }] }));
  return server;
}

const mcpHandler = createMcpHandler(buildMcpServer);
const nodeMcpHandler = toNodeHandler(mcpHandler);
const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://aura.local");
  if (url.pathname === "/healthz") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, devices: devices.size, events: eventJournal.count(), tasks: taskStore.list(1000).length }));
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
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, req, deviceId));
});
wss.on("connection", (ws: WebSocket, _req: IncomingMessage, deviceId: string) => {
  connectDevice(deviceId, ws);
  send(ws, { type: "hello", protocol: 1, deviceId, capabilities: { task: true, event: true, status: true } });
  ws.on("message", raw => {
    let message: JsonRecord;
    try {
      const parsed: unknown = JSON.parse(raw.toString());
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return;
      message = parsed as JsonRecord;
    } catch { return; }
    const device = devices.get(deviceId);
    if (device) device.lastSeenAt = Date.now();
    if (message.type === "event" || message.type === "result") {
      try {
        const id = rememberEvent(message, deviceId);
        send(ws, { type: "ack", id, protocol: 1 });
        if (message.type === "result") finishTask(message);
      } catch (error) {
        console.error("AURA event was not ACKed; durable journal rejected it", error);
        ws.close(1013, "durable journal unavailable");
      }
      return;
    }
    if (message.type === "status") return;
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
