import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DeviceRegistry } from "./src/device-registry.js";

const dir = mkdtempSync(join(tmpdir(), "aura-registry-"));
const path = join(dir, "devices.json");
const configured = { "test-device": "configured-device-credential" };

try {
  const first = new DeviceRegistry(path, configured);
  assert.equal(first.isAuthorized("test-device", configured["test-device"]), true);
  assert.equal(first.isAuthorized("test-device", "wrong-credential"), false);
  assert.equal(first.revoke("test-device"), true);
  assert.equal(first.isAuthorized("test-device", configured["test-device"]), false);
  assert.equal(first.isRevoked("test-device"), true);

  const restarted = new DeviceRegistry(path, configured);
  assert.equal(restarted.isAuthorized("test-device", configured["test-device"]), false);
  assert.equal(restarted.restore("test-device"), true);
  assert.equal(restarted.isAuthorized("test-device", configured["test-device"]), true);

  const enrolled = "enrolled-device-credential";
  assert.equal(restarted.enroll("phone-1", enrolled), true);
  assert.equal(restarted.isAuthorized("phone-1", enrolled), true);
  const rotated = "rotated-device-credential";
  assert.equal(restarted.rotate("phone-1", rotated), true);
  assert.equal(restarted.isAuthorized("phone-1", enrolled), false);
  assert.equal(restarted.isAuthorized("phone-1", rotated), true);

  const restartedAgain = new DeviceRegistry(path, configured);
  assert.equal(restartedAgain.isAuthorized("phone-1", rotated), true);
  assert.equal(restartedAgain.isAuthorized("phone-1", enrolled), false);
  const persisted = JSON.parse(readFileSync(path, "utf8"));
  assert.deepEqual(persisted.revoked, []);
  assert.ok(persisted.credentials["phone-1"]);
  console.log("AURA device registry tests passed");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
