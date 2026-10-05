import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { createHash, timingSafeEqual } from "node:crypto";

type TokenMap = Record<string, string>;
type RegistryState = { revoked: string[]; credentials: TokenMap };

/** Durable device identity lifecycle state. Bearer credentials are stored only as SHA-256 hashes. */
export class DeviceRegistry {
  private revoked = new Set<string>();
  private credentials = new Map<string, string>();

  constructor(
    private readonly path: string,
    private readonly configuredTokens: TokenMap,
  ) { this.load(); }

  isAuthorized(id: string, token: string): boolean {
    if (!id || this.revoked.has(id)) return false;
    const expected = this.configuredTokens[id];
    if (expected) return safeEqual(token, expected);
    const hash = this.credentials.get(id);
    return !!hash && safeEqual(hashToken(token), hash);
  }

  isConfigured(id: string): boolean {
    return Object.prototype.hasOwnProperty.call(this.configuredTokens, id) || this.credentials.has(id);
  }

  isRevoked(id: string): boolean { return this.revoked.has(id); }
  configuredIds(): string[] { return Array.from(new Set([...Object.keys(this.configuredTokens), ...this.credentials.keys()])).filter(id => id !== "*"); }

  enroll(id: string, token: string): boolean {
    if (!id || !token || this.isConfigured(id)) return false;
    this.credentials.set(id, hashToken(token));
    this.revoked.delete(id);
    this.persist();
    return true;
  }

  rotate(id: string, token: string): boolean {
    if (!id || !token || !this.credentials.has(id) || this.revoked.has(id)) return false;
    this.credentials.set(id, hashToken(token));
    this.persist();
    return true;
  }

  revoke(id: string): boolean {
    if (!id || !this.isConfigured(id)) return false;
    const changed = !this.revoked.has(id);
    this.revoked.add(id);
    if (changed) this.persist();
    return changed;
  }

  restore(id: string): boolean {
    if (!id || !this.isConfigured(id)) return false;
    const changed = this.revoked.delete(id);
    if (changed) this.persist();
    return changed;
  }

  private load(): void {
    try {
      if (!existsSync(this.path)) return;
      const value: unknown = JSON.parse(readFileSync(this.path, "utf8"));
      if (Array.isArray(value)) { for (const id of value) if (typeof id === "string" && id) this.revoked.add(id); return; }
      if (!value || typeof value !== "object") return;
      const state = value as Partial<RegistryState>;
      if (Array.isArray(state.revoked)) for (const id of state.revoked) if (typeof id === "string" && id) this.revoked.add(id);
      if (state.credentials && typeof state.credentials === "object") {
        for (const [id, hash] of Object.entries(state.credentials)) if (/^[a-f0-9]{64}$/.test(hash)) this.credentials.set(id, hash);
      }
    } catch { this.revoked.clear(); this.credentials.clear(); }
  }

  private persist(): void {
    mkdirSync(dirname(this.path), { recursive: true });
    const temp = `${this.path}.tmp`;
    const state: RegistryState = { revoked: Array.from(this.revoked).sort(), credentials: Object.fromEntries(Array.from(this.credentials.entries()).sort()) };
    writeFileSync(temp, JSON.stringify(state, null, 2), { encoding: "utf8", mode: 0o600 });
    renameSync(temp, this.path);
  }
}

function hashToken(token: string): string { return createHash("sha256").update(token, "utf8").digest("hex"); }
function safeEqual(a: string, b: string): boolean {
  const aa = Buffer.from(a); const bb = Buffer.from(b);
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
