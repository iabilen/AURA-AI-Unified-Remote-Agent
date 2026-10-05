import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DeviceRegistry } from "./src/device-registry.js";

const dir = mkdtempSync(join(tmpdir(), "aura-registry-"));
const path = join(dir, "devices.json");
const tokens = { "test-device": "aura-test-device-token-1234567890" };

try {
  const first = new DeviceRegistry(path, tokens);
  assert.equal(first.isAuthorized("test-device", tokens["test-device"]), true);
  assert.equal(first.isAuthorized("test-device", "wrong-token"), false);
  assert.equal(first.revoke("test-device"), true);
  assert.equal(first.isAuthorized("test-device", tokens["test-device"]), false);
  assert.equal(first.isRevoked("test-device"), true);

  const restarted = new DeviceRegistry(path, tokens);
  assert.equal(restarted.isAuthorized("test-device", tokens["test-device"]), false);
  assert.equal(restarted.restore("test-device"), true);
  assert.equal(restarted.isAuthorized("test-device", tokens["test-device"]), true);

  const persisted = JSON.parse(readFileSync(path, "utf8"));
  assert.deepEqual(persisted, []);
  console.log("AURA device registry tests passed");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
