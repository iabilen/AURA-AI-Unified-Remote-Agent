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
 * Consumer leases and ACK state are stored in the same atomic journal. Pending
 * records are never pruned; only explicitly consumed records may expire.
 */
export class DurableEventJournal {
  private records: JsonRecord[];
  private readonly path: string;
  private readonly maxRecords: number;
  private readonly maxEventBytes: number;
  private readonly maxJournalBytes: number;

  constructor(
    path: string,
    maxRecords = 1_000,
    maxEventBytes = 64 * 1024,
    maxJournalBytes = 32 * 1024 * 1024,
  ) {
    if (!path.trim()) throw new Error("AURA journal path is required");
    if (!Number.isSafeInteger(maxRecords) || maxRecords < 1) {
      throw new Error("AURA journal maxRecords must be a positive safe integer");
    }
    if (!Number.isSafeInteger(maxEventBytes) || maxEventBytes < 1) {
      throw new Error("AURA journal maxEventBytes must be a positive safe integer");
    }
    if (!Number.isSafeInteger(maxJournalBytes) || maxJournalBytes < 1) {
      throw new Error("AURA journal maxJournalBytes must be a positive safe integer");
    }
    if (maxEventBytes > maxJournalBytes) {
      throw new Error("AURA journal maxEventBytes cannot exceed maxJournalBytes");
    }
    this.path = path;
    this.maxRecords = maxRecords;
    this.maxEventBytes = maxEventBytes;
    this.maxJournalBytes = maxJournalBytes;
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
    this.assertEventSize(record);
    const next = [...this.records, record];
    this.assertJournalSize(next);
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

  /** Claim pending records or reclaim expired leases. Claim state is durable before return. */
  claim(consumerId: string, limit = 20, leaseMs = 30_000): JsonRecord[] {
    if (!consumerId.trim()) throw new Error("AURA journal consumer ID is required");
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100) throw new Error("AURA journal claim limit must be 1..100");
    if (!Number.isSafeInteger(leaseMs) || leaseMs < 1_000 || leaseMs > 300_000) throw new Error("AURA journal lease must be 1000..300000ms");
    const now = Date.now();
    const eligible = this.records.filter((row) => row.consumedAt === undefined &&
      (typeof row.leaseUntil !== "number" || row.leaseUntil <= now || row.leaseOwner === consumerId)).slice(0, limit);
    if (eligible.length === 0) return [];
    const ids = new Set(eligible.map((row) => `${String(row.deviceId)}\u0000${String(row.id)}`));
    const next = this.records.map((row) => ids.has(`${String(row.deviceId)}\u0000${String(row.id)}`)
      ? { ...row, leaseOwner: consumerId, leaseUntil: now + leaseMs, deliveryAttempts: Number(row.deliveryAttempts ?? 0) + 1 }
      : row);
    this.persistAtomically(next);
    this.records = next;
    return this.records.filter((row) => ids.has(`${String(row.deviceId)}\u0000${String(row.id)}`)).map((row) => ({ ...row }));
  }

  acknowledge(consumerId: string, deviceId: string, eventId: string): boolean {
    const index = this.records.findIndex((row) => row.deviceId === deviceId && row.id === eventId);
    if (index < 0) return false;
    const row = this.records[index];
    if (row.consumedAt !== undefined) return true;
    if (row.leaseOwner !== consumerId || typeof row.leaseUntil !== "number" || row.leaseUntil <= Date.now()) return false;
    const next = this.records.map((item, i) => i === index ? { ...item, consumedAt: Date.now(), leaseOwner: null, leaseUntil: null } : item);
    this.persistAtomically(next);
    this.records = next;
    return true;
  }

  release(consumerId: string, deviceId: string, eventId: string): boolean {
    const index = this.records.findIndex((row) => row.deviceId === deviceId && row.id === eventId);
    if (index < 0) return false;
    const row = this.records[index];
    if (row.consumedAt !== undefined || row.leaseOwner !== consumerId) return false;
    const next = this.records.map((item, i) => i === index ? { ...item, leaseOwner: null, leaseUntil: null } : item);
    this.persistAtomically(next);
    this.records = next;
    return true;
  }

  pruneConsumed(retentionMs: number, now = Date.now()): number {
    if (!Number.isSafeInteger(retentionMs) || retentionMs < 86_400_000) throw new Error("AURA consumed-event retention must be at least 24 hours");
    const next = this.records.filter((row) => row.consumedAt === undefined || typeof row.consumedAt !== "number" || now - row.consumedAt < retentionMs);
    const removed = this.records.length - next.length;
    if (removed > 0) { this.persistAtomically(next); this.records = next; }
    return removed;
  }

  count(): number {
    return this.records.length;
  }

  private loadStrictly(): JsonRecord[] {
    if (!existsSync(this.path)) return [];
    let raw: string;
    let parsed: unknown;
    try {
      raw = readFileSync(this.path, "utf8");
      if (Buffer.byteLength(raw, "utf8") > this.maxJournalBytes) {
        throw new Error("AURA relay journal exceeds configured byte capacity");
      }
      parsed = JSON.parse(raw);
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
    const records = rows.map((row) => {
      if (!isRecord(row)) throw new Error("AURA relay journal contains a malformed record");
      const id = requiredString(row.id, "event ID");
      const deviceId = requiredString(row.deviceId, "device ID");
      const key = `${deviceId}\u0000${id}`;
      if (seen.has(key)) throw new Error("AURA relay journal contains duplicate event IDs");
      seen.add(key);
      const record = cloneJsonRecord(row);
      this.assertEventSize(record);
      return record;
    });

    // The legacy store was a bare JSON array. Replace it only after every row
    // has passed validation and the complete versioned file is durably written.
    if (Array.isArray(parsed)) this.persistAtomically(records);
    return records;
  }

  private assertEventSize(record: JsonRecord): void {
    const serialized = JSON.stringify(record);
    if (Buffer.byteLength(serialized, "utf8") > this.maxEventBytes) {
      throw new Error("AURA relay event exceeds configured per-event byte capacity");
    }
  }

  private assertJournalSize(records: JsonRecord[]): void {
    const serialized = JSON.stringify({ schemaVersion: 1, records } satisfies JournalState);
    if (Buffer.byteLength(serialized, "utf8") > this.maxJournalBytes) {
      throw new Error("AURA relay journal is full; event was not accepted or discarded");
    }
  }

  private persistAtomically(records: JsonRecord[]): void {
    const directory = dirname(this.path);
    mkdirSync(directory, { recursive: true });
    const temporaryPath = `${this.path}.${process.pid}.${randomUUID()}.tmp`;
    const serialized = JSON.stringify({ schemaVersion: 1, records } satisfies JournalState);
    if (Buffer.byteLength(serialized, "utf8") > this.maxJournalBytes) {
      throw new Error("AURA relay journal is full; event was not accepted or discarded");
    }
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
