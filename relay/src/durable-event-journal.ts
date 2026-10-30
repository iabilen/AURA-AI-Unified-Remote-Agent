import {
  closeSync,
  existsSync,
  fsyncSync,
  mkdirSync,
  openSync,
  readFileSync,
  renameSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { dirname } from "node:path";
import { randomUUID } from "node:crypto";

type JsonRecord = Record<string, unknown>;

export type JournalAcceptResult =
  | { status: "accepted"; id: string; deviceId: string }
  | { status: "duplicate"; id: string; deviceId: string };

type JournalState = {
  schemaVersion: 1;
  records: JsonRecord[];
};

/**
 * Durable relay-side journal for device-originated events.
 *
 * ACK contract:
 * - Call accept() before sending an ACK.
 * - "accepted" means the event has been atomically persisted by this process.
 * - "duplicate" means the same device/event ID was already persisted.
 * - Any persistence, corruption, validation, or capacity error throws. Callers
 *   MUST NOT ACK when accept() throws.
 *
 * Records are never silently evicted. Capacity exhaustion is backpressure:
 * reject the new event and leave all existing records intact.
 *
 * This class is additive and is not wired into the existing relay entrypoint.
 */
export class DurableEventJournal {
  private records: JsonRecord[];
  private readonly path: string;
  private readonly maxRecords: number;

  constructor(path: string, maxRecords = 10_000) {
    if (!path.trim()) throw new Error("AURA journal path is required");
    if (!Number.isSafeInteger(maxRecords) || maxRecords < 1) {
      throw new Error("AURA journal maxRecords must be a positive safe integer");
    }
    this.path = path;
    this.maxRecords = maxRecords;
    this.records = this.loadStrictly();
  }

  accept(event: JsonRecord, deviceId: string): JournalAcceptResult {
    if (!deviceId.trim()) throw new Error("AURA journal device ID is required");
    const snapshot = cloneJsonRecord(event);
    const id = requiredString(snapshot.id, "event ID");
    if (this.records.some((item) => item.id === id && item.deviceId === deviceId)) {
      return { status: "duplicate", id, deviceId };
    }
    if (this.records.length >= this.maxRecords) {
      throw new Error("AURA relay journal is full; event was not accepted or discarded");
    }

    const record = { ...snapshot, id, deviceId, receivedAt: Date.now() };
    const next = [...this.records, record];
    this.persistAtomically(next);
    this.records = next;
    return { status: "accepted", id, deviceId };
  }

  list(deviceId?: string, limit = 20): JsonRecord[] {
    if (!Number.isSafeInteger(limit) || limit < 1) {
      throw new Error("AURA journal list limit must be a positive safe integer");
    }
    return this.records
      .filter((event) => !deviceId || event.deviceId === deviceId)
      .slice(-limit)
      .map((event) => ({ ...event }));
  }

  count(): number {
    return this.records.length;
  }

  private loadStrictly(): JsonRecord[] {
    if (!existsSync(this.path)) return [];
    let parsed: unknown;
    try {
      parsed = JSON.parse(readFileSync(this.path, "utf8"));
    } catch (error) {
      throw new Error("AURA relay journal is unreadable; refusing to start with an empty journal", {
        cause: error,
      });
    }

    // Accept the legacy array shape for a controlled migration. Never filter
    // malformed rows: one invalid record makes the journal unavailable.
    const rows: unknown[] = Array.isArray(parsed)
      ? parsed
      : isRecord(parsed) && parsed.schemaVersion === 1 && Array.isArray(parsed.records)
        ? parsed.records
        : (() => { throw new Error("AURA relay journal has an unsupported or invalid schema"); })();

    if (rows.length > this.maxRecords) {
      throw new Error("AURA relay journal exceeds configured capacity; no records were removed");
    }

    const seen = new Set<string>();
    return rows.map((row) => {
      if (!isRecord(row)) throw new Error("AURA relay journal contains a malformed record");
      const id = requiredString(row.id, "event ID");
      const deviceId = requiredString(row.deviceId, "device ID");
      const key = `${deviceId}\u0000${id}`;
      if (seen.has(key)) throw new Error("AURA relay journal contains duplicate event IDs");
      seen.add(key);
      return cloneJsonRecord(row);
    });
  }

  private persistAtomically(records: JsonRecord[]): void {
    const directory = dirname(this.path);
    mkdirSync(directory, { recursive: true });
    const temporaryPath = `${this.path}.${process.pid}.${randomUUID()}.tmp`;
    const serialized = JSON.stringify({ schemaVersion: 1, records } satisfies JournalState);
    let fd: number | undefined;

    try {
      fd = openSync(temporaryPath, "wx", 0o600);
      writeFileSync(fd, serialized, { encoding: "utf8" });
      fsyncSync(fd);
      closeSync(fd);
      fd = undefined;

      renameSync(temporaryPath, this.path);

      // Persist the directory entry as well as the file contents where the
      // platform supports directory fsync. Failure is surfaced, never ACKed.
      const directoryFd = openSync(directory, "r");
      try {
        fsyncSync(directoryFd);
      } finally {
        closeSync(directoryFd);
      }
    } catch (error) {
      if (fd !== undefined) {
        try { closeSync(fd); } catch { /* preserve original error */ }
      }
      try {
        if (existsSync(temporaryPath)) unlinkSync(temporaryPath);
      } catch { /* preserve original error */ }
      throw new Error("AURA relay journal commit failed; event must not be ACKed", {
        cause: error,
      });
    }
  }
}

function isRecord(value: unknown): value is JsonRecord {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function requiredString(value: unknown, label: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`AURA journal ${label} is required`);
  }
  return value;
}

function cloneJsonRecord(value: JsonRecord): JsonRecord {
  let serialized: string;
  try {
    serialized = JSON.stringify(value);
  } catch (error) {
    throw new Error("AURA journal event is not JSON-serializable", { cause: error });
  }
  if (serialized === undefined) throw new Error("AURA journal event is not JSON-serializable");
  const clone: unknown = JSON.parse(serialized);
  if (!isRecord(clone)) throw new Error("AURA journal event must be a JSON object");
  return clone;
}
