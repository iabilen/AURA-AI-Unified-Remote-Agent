import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";

const port = 18988;
const dir = mkdtempSync(join(tmpdir(), "aura-relay-shutdown-"));
const env = { ...process.env, PORT: String(port), AURA_MCP_TOKEN: "aura-test-mcp-token-1234567890", AURA_DEVICE_TOKENS: JSON.stringify({ "shutdown-test": "aura-test-device-token-1234567890" }), AURA_EVENT_STORE_PATH: join(dir, "events.json") };

function startRelay() {
  return spawn(process.execPath, ["node_modules/tsx/dist/cli.mjs", "src/index.ts"], { cwd: process.cwd(), env, stdio: ["ignore", "ignore", "pipe"] });
}

async function waitForHealth() {
  for (let i = 0; i < 50; i++) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (response.ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("relay did not become healthy");
}

async function assertTerminates(signal) {
  const child = startRelay();
  try {
    await waitForHealth();
    child.kill(signal);
    const result = await Promise.race([
      new Promise((resolve) => child.once("exit", (code, receivedSignal) => resolve({ code, receivedSignal }))),
      new Promise((_, reject) => setTimeout(() => reject(new Error(`relay did not terminate after ${signal}`)), 2000)),
    ]);
    if (result.receivedSignal !== signal) throw new Error(`expected ${signal}, got ${result.receivedSignal ?? `exit ${result.code}`}`);
  } finally {
    if (!child.killed) child.kill("SIGKILL");
  }
}

try {
  await assertTerminates("SIGTERM");
  await assertTerminates("SIGINT");
  console.log("shutdown regression passed");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
