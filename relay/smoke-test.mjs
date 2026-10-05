import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import { WebSocket } from "ws";

const port = 18987;
const token = "aura-test-mcp-token-1234567890";
const deviceToken = "aura-test-device-token-1234567890";
const deviceId = "smoke-device";
const dir = mkdtempSync(join(tmpdir(), "aura-relay-"));
const store = join(dir, "events.json");

function startRelay() {
  const child = spawn(process.execPath, ["node_modules/tsx/dist/cli.mjs", "src/index.ts"], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: String(port), AURA_MCP_TOKEN: token, AURA_DEVICE_TOKENS: JSON.stringify({ [deviceId]: deviceToken }), AURA_EVENT_STORE_PATH: store },
    stdio: ["ignore", "pipe", "pipe"],
  });
  return child;
}

async function waitForHealth() {
  for (let i = 0; i < 50; i++) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (response.ok) return response.json();
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("relay did not become healthy");
}

function waitForAck(ws, expectedId) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("ACK timeout")), 5000);
    ws.on("message", (raw) => {
      const message = JSON.parse(raw.toString());
      if (message.type === "ack" && message.id === expectedId) {
        clearTimeout(timer);
        resolve();
      }
    });
    ws.on("error", reject);
  });
}

async function connectAndSend() {
  const ws = new WebSocket(`ws://127.0.0.1:${port}/device`, { headers: { Authorization: `Bearer ${deviceToken}`, "X-AURA-Device": deviceId } });
  await new Promise((resolve, reject) => { ws.once("open", resolve); ws.once("error", reject); });
  const event = { type: "event", id: "smoke-event-1", source: "smoke", priority: 1, payload: { message: "persist me" } };
  const ack = waitForAck(ws, event.id);
  ws.send(JSON.stringify(event));
  await ack;
  ws.close();
}

async function main() {
  let relay = startRelay();
  try {
    const first = await waitForHealth();
    if (first.events !== 0) throw new Error(`expected empty store, got ${first.events}`);
    await connectAndSend();
    const afterEvent = await waitForHealth();
    if (afterEvent.events !== 1) throw new Error(`expected one stored event, got ${afterEvent.events}`);
    relay.kill("SIGTERM");
    await new Promise((resolve) => relay.once("exit", resolve));
    relay = startRelay();
    const afterRestart = await waitForHealth();
    if (afterRestart.events !== 1) throw new Error(`event was not durable across restart: ${afterRestart.events}`);
    const unauthorized = await fetch(`http://127.0.0.1:${port}/mcp`);
    if (unauthorized.status !== 401) throw new Error(`expected MCP auth 401, got ${unauthorized.status}`);
    const authorized = await fetch(`http://127.0.0.1:${port}/mcp`, { headers: { Authorization: `Bearer ${token}` } });
    if (authorized.status === 401) throw new Error("valid MCP token was rejected");
    console.log("AURA relay smoke test passed: event ACK, persistence, restart recovery, and MCP auth gate");
  } finally {
    relay.kill("SIGTERM");
    rmSync(dir, { recursive: true, force: true });
  }
}

main().catch((error) => { console.error(error); process.exit(1); });
