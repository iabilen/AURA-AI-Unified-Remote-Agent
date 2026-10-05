import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { RelayDatabase } from "./src/relay-database.js";

async function main(): Promise<void> {
  const disabled = new RelayDatabase("");
  assert.equal(disabled.enabled, false);
  await disabled.init();
  await disabled.recordDevice("disabled-device", false, 0, 0);
  await disabled.recordEvent({ id: "disabled-event", type: "test" }, "disabled-device", "disabled-event", 0);
  await disabled.recordTask("disabled-task", "disabled-device", "test", "noop", 0);
  await disabled.completeTask("disabled-task", "completed", null, 1);
  assert.deepEqual(await disabled.listEvents(), []);
  assert.equal(await disabled.countEvents(), 0);
  await disabled.close();

  const connectionString = process.env.AURA_RELAY_DATABASE_URL ?? process.env.DATABASE_URL ?? "";
  if (!connectionString) {
    console.log("AURA relay database tests passed (database integration skipped: no DATABASE_URL)");
    return;
  }

  const db = new RelayDatabase(connectionString);
  const deviceId = `test-${randomUUID()}`;
  const eventId = randomUUID();
  const taskId = randomUUID();
  try {
    await db.init();
    assert.equal(db.enabled, true);
    await db.recordDevice(deviceId, true, 100, 100);
    await db.recordEvent({ id: eventId, type: "test", payload: { ok: true } }, deviceId, eventId, 101);
    const events = await db.listEvents(deviceId, 10);
    assert.equal(events.length, 1);
    assert.equal(events[0]?.id, eventId);
    await db.recordTask(taskId, deviceId, "test", "database test", 102);
    await db.completeTask(taskId, "completed", { ok: true }, 103);
    assert.ok((await db.countEvents()) >= 1);
    console.log("AURA relay PostgreSQL integration tests passed");
  } finally {
    await db.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
