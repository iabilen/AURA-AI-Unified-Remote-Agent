import {
  closeSync, existsSync, fsyncSync, mkdirSync, openSync, readFileSync,
  renameSync, unlinkSync, writeFileSync,
} from "node:fs";
import { dirname } from "node:path";
import { randomUUID } from "node:crypto";

export type TaskStatus =
  | "queued" | "dispatched" | "awaiting_device"
  | "completed" | "failed" | "cancelled" | "expired";

export type DurableTask = {
  id: string; deviceId: string; idempotencyKey: string; status: TaskStatus;
  task: string; createdAt: number; updatedAt: number; expiresAt: number;
  result?: unknown; error?: string;
};
type StoreState = { schemaVersion: 1; tasks: DurableTask[] };

/** Crash-durable, single-writer task ledger. Stale locks require operator review. */
export class DurableTaskStore {
  private readonly lockPath: string;
  private readonly lockFd: number;
  private tasks: DurableTask[];

  constructor(private readonly path: string, private readonly maxTasks = 2000) {
    if (!path.trim() || !Number.isSafeInteger(maxTasks) || maxTasks < 1) throw new Error("Invalid task store configuration");
    mkdirSync(dirname(path), { recursive: true });
    this.lockPath = path + ".lock";
    let fd: number;
    try {
      fd = openSync(this.lockPath, "wx", 0o600);
      writeFileSync(fd, JSON.stringify({ pid: process.pid, acquiredAt: Date.now() }));
      fsyncSync(fd);
    } catch (error) {
      throw new Error("AURA task store is locked; refusing concurrent or unsafe startup", { cause: error });
    }
    this.lockFd = fd;
    try {
      this.tasks = this.load();
    } catch (error) {
      try { closeSync(this.lockFd); } catch { /* preserve load error */ }
      try { unlinkSync(this.lockPath); } catch { /* preserve load error */ }
      throw error;
    }
    process.once("exit", () => this.close());
  }

  create(input: { id: string; deviceId: string; task: string; idempotencyKey: string; expiresAt: number }): DurableTask {
    if (![input.id, input.deviceId, input.task, input.idempotencyKey].every(v => typeof v === "string" && v.trim()) ||
        !Number.isFinite(input.expiresAt)) throw new Error("Invalid durable task");
    const duplicate = this.tasks.find(t => t.deviceId === input.deviceId && t.idempotencyKey === input.idempotencyKey);
    if (duplicate) return { ...duplicate };
    if (this.tasks.length >= this.maxTasks) throw new Error("AURA task store is full; backpressure applied");
    const now = Date.now();
    const task: DurableTask = { ...input, status: "queued", createdAt: now, updatedAt: now };
    this.commit([...this.tasks, task]);
    return { ...task };
  }

  transition(id: string, status: TaskStatus, details: { result?: unknown; error?: string } = {}): DurableTask {
    const current = this.tasks.find(t => t.id === id);
    if (!current) throw new Error("Unknown durable task");
    const allowed: Record<TaskStatus, TaskStatus[]> = {
      queued: ["dispatched","cancelled","expired","failed"],
      dispatched: ["awaiting_device","completed","failed","cancelled","expired"],
      awaiting_device: ["completed","failed","cancelled","expired","dispatched"],
      completed: [], failed: [], cancelled: [], expired: [],
    };
    if (current.status !== status && !allowed[current.status].includes(status)) throw new Error(`Invalid task transition: ${current.status} -> ${status}`);
    const next = { ...current, ...details, status, updatedAt: Date.now() };
    this.commit(this.tasks.map(t => t.id === id ? next : t));
    return { ...next };
  }

  get(id: string): DurableTask | undefined {
    const found = this.tasks.find(t => t.id === id);
    return found ? { ...found } : undefined;
  }
  list(limit = 100): DurableTask[] {
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 1000) throw new Error("Invalid task list limit");
    return this.tasks.slice(-limit).reverse().map(t => ({ ...t }));
  }
  close(): void {
    try { closeSync(this.lockFd); } catch { /* already closed */ }
    try { if (existsSync(this.lockPath)) unlinkSync(this.lockPath); } catch { /* process exit */ }
  }

  private load(): DurableTask[] {
    if (!existsSync(this.path)) return [];
    const parsed: unknown = JSON.parse(readFileSync(this.path, "utf8"));
    if (!parsed || typeof parsed !== "object" || (parsed as StoreState).schemaVersion !== 1 ||
        !Array.isArray((parsed as StoreState).tasks)) throw new Error("AURA task store is corrupt or unsupported");
    const tasks = (parsed as StoreState).tasks;
    const ids = new Set<string>(), keys = new Set<string>();
    for (const item of tasks) {
      if (!item || typeof item.id !== "string" || !item.id || typeof item.deviceId !== "string" ||
          !item.deviceId || typeof item.idempotencyKey !== "string" || !item.idempotencyKey ||
          typeof item.task !== "string" || !["queued","dispatched","awaiting_device","completed","failed","cancelled","expired"].includes(item.status) ||
          ids.has(item.id) || keys.has(item.deviceId + "\0" + item.idempotencyKey) ||
          !Number.isFinite(item.createdAt) || !Number.isFinite(item.updatedAt) || !Number.isFinite(item.expiresAt)) {
        throw new Error("AURA task store contains invalid or duplicate records");
      }
      ids.add(item.id); keys.add(item.deviceId + "\0" + item.idempotencyKey);
    }
    if (tasks.length > this.maxTasks) throw new Error("AURA task store exceeds configured capacity");
    return tasks;
  }

  private commit(tasks: DurableTask[]): void {
    const temp = this.path + "." + process.pid + "." + randomUUID() + ".tmp";
    let fd: number | undefined;
    try {
      fd = openSync(temp, "wx", 0o600);
      writeFileSync(fd, JSON.stringify({ schemaVersion: 1, tasks } satisfies StoreState), "utf8");
      fsyncSync(fd); closeSync(fd); fd = undefined;
      renameSync(temp, this.path);
      const dirFd = openSync(dirname(this.path), "r");
      try { fsyncSync(dirFd); } finally { closeSync(dirFd); }
      this.tasks = tasks;
    } catch (error) {
      if (fd !== undefined) try { closeSync(fd); } catch { /* preserve original */ }
      try { unlinkSync(temp); } catch { /* preserve original */ }
      throw new Error("AURA task state was not durably committed", { cause: error });
    }
  }
}
