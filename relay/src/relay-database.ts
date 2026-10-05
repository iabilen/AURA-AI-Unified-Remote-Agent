import { Pool } from "pg";

type JsonRecord = Record<string, unknown>;

export class RelayDatabase {
  private readonly pool: Pool | null;
  readonly enabled: boolean;

  constructor(connectionString = "") {
    this.enabled = connectionString.length > 0;
    this.pool = this.enabled
      ? new Pool({ connectionString, max: 5, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000 })
      : null;
    this.pool?.on("error", (error) => console.error("AURA relay database pool error", error.message));
  }

  async init(): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS aura_relay_devices (
        device_id TEXT PRIMARY KEY,
        connected BOOLEAN NOT NULL DEFAULT FALSE,
        connected_at BIGINT,
        last_seen_at BIGINT,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS aura_relay_events (
        id TEXT NOT NULL,
        device_id TEXT NOT NULL,
        type TEXT,
        received_at BIGINT NOT NULL,
        payload JSONB NOT NULL,
        PRIMARY KEY (id, device_id)
      );
      CREATE INDEX IF NOT EXISTS aura_relay_events_device_received_idx
        ON aura_relay_events (device_id, received_at DESC);
      CREATE TABLE IF NOT EXISTS aura_relay_tasks (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        source TEXT NOT NULL,
        task TEXT NOT NULL,
        status TEXT NOT NULL,
        created_at BIGINT NOT NULL,
        completed_at BIGINT,
        result JSONB
      );
      CREATE INDEX IF NOT EXISTS aura_relay_tasks_device_created_idx
        ON aura_relay_tasks (device_id, created_at DESC);
    `);
  }

  async recordDevice(deviceId: string, connected: boolean, connectedAt: number, lastSeenAt: number): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(`
      INSERT INTO aura_relay_devices (device_id, connected, connected_at, last_seen_at)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (device_id) DO UPDATE SET
        connected = EXCLUDED.connected,
        connected_at = EXCLUDED.connected_at,
        last_seen_at = EXCLUDED.last_seen_at,
        updated_at = NOW()
    `, [deviceId, connected, connectedAt, lastSeenAt]);
  }

  async recordEvent(event: JsonRecord, deviceId: string, id: string, receivedAt: number): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(`
      INSERT INTO aura_relay_events (id, device_id, type, received_at, payload)
      VALUES ($1, $2, $3, $4, $5)
      ON CONFLICT (id, device_id) DO NOTHING
    `, [id, deviceId, typeof event.type === "string" ? event.type : null, receivedAt, event]);
  }

  async listEvents(deviceId?: string, limit = 20): Promise<JsonRecord[]> {
    if (!this.pool) return [];
    const result = deviceId
      ? await this.pool.query(`SELECT payload FROM aura_relay_events WHERE device_id = $1 ORDER BY received_at DESC LIMIT $2`, [deviceId, limit])
      : await this.pool.query(`SELECT payload FROM aura_relay_events ORDER BY received_at DESC LIMIT $1`, [limit]);
    return result.rows.map((row) => row.payload as JsonRecord).reverse();
  }

  async countEvents(): Promise<number> {
    if (!this.pool) return 0;
    const result = await this.pool.query("SELECT COUNT(*)::int AS count FROM aura_relay_events");
    return result.rows[0]?.count ?? 0;
  }

  async recordTask(id: string, deviceId: string, source: string, task: string, createdAt: number): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(`
      INSERT INTO aura_relay_tasks (id, device_id, source, task, status, created_at)
      VALUES ($1, $2, $3, $4, 'pending', $5)
      ON CONFLICT (id) DO NOTHING
    `, [id, deviceId, source, task, createdAt]);
  }

  async completeTask(id: string, status: string, result: JsonRecord | null, completedAt: number): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(`
      UPDATE aura_relay_tasks SET status = $2, result = $3, completed_at = $4 WHERE id = $1
    `, [id, status, result, completedAt]);
  }

  async close(): Promise<void> {
    await this.pool?.end();
  }
}
