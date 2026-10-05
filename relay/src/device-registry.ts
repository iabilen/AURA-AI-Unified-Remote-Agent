import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { timingSafeEqual } from "node:crypto";

type TokenMap = Record<string, string>;

/**
 * Durable device identity revocation state.
 *
 * Device credentials remain supplied by the deployment environment; this file
 * stores only lifecycle state (currently revoked device IDs), never bearer tokens.
 */
export class DeviceRegistry {
  private revoked = new Set<string>();

  constructor(
    private readonly path: string,
    private readonly configuredTokens: TokenMap,
  ) {
    this.load();
  }

  isAuthorized(id: string, token: string): boolean {
    if (!id || this.revoked.has(id)) return false;
    const expected = this.configuredTokens[id] ?? this.configuredTokens["*"];
    if (!expected) return false;
    const aa = Buffer.from(token);
    const bb = Buffer.from(expected);
    return aa.length === bb.length && timingSafeEqual(aa, bb);
  }

  isConfigured(id: string): boolean {
    return Object.prototype.hasOwnProperty.call(this.configuredTokens, id) ||
      Object.prototype.hasOwnProperty.call(this.configuredTokens, "*");
  }

  isRevoked(id: string): boolean { return this.revoked.has(id); }

  configuredIds(): string[] {
    return Object.keys(this.configuredTokens).filter((id) => id !== "*");
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
      if (Array.isArray(value)) {
        for (const id of value) if (typeof id === "string" && id.length > 0) this.revoked.add(id);
      }
    } catch {
      this.revoked.clear();
    }
  }

  private persist(): void {
    mkdirSync(dirname(this.path), { recursive: true });
    const temp = `${this.path}.tmp`;
    writeFileSync(temp, JSON.stringify([...this.revoked].sort()), { encoding: "utf8", mode: 0o600 });
    renameSync(temp, this.path);
  }
}
