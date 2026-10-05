import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

type JsonRecord = Record<string, unknown>;

/** Small file-backed event journal. The file is rewritten atomically and bounded. */
export class DurableEventStore {
  private events: JsonRecord[];
  private readonly path: string;
  private readonly limit: number;

  constructor(path: string, limit: number) {
    this.path = path;
    this.limit = limit;
    this.events = this.load();
  }

  remember(event: JsonRecord): void {
    const id = typeof event.id === "string" ? event.id : "";
    const deviceId = typeof event.deviceId === "string" ? event.deviceId : "";
    if (this.events.some((item) => item.id === id && item.deviceId === deviceId)) return;
    this.events.push(event);
    if (this.events.length > this.limit) this.events.splice(0, this.events.length - this.limit);
    this.persist();
  }

  list(deviceId?: string, limit = 20): JsonRecord[] {
    return this.events.filter((event) => !deviceId || event.deviceId === deviceId).slice(-limit);
  }

  count(): number { return this.events.length; }

  private load(): JsonRecord[] {
    try {
      if (!existsSync(this.path)) return [];
      const value: unknown = JSON.parse(readFileSync(this.path, "utf8"));
      return Array.isArray(value)
        ? value.filter((item): item is JsonRecord => !!item && typeof item === "object" && !Array.isArray(item)).slice(-this.limit)
        : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    mkdirSync(dirname(this.path), { recursive: true });
    const temp = `${this.path}.tmp`;
    writeFileSync(temp, JSON.stringify(this.events), { encoding: "utf8", mode: 0o600 });
    renameSync(temp, this.path);
  }
}
