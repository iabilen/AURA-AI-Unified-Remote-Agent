# AURA Relay / MCP

The relay exposes the Android bridge at `/device`, the authenticated orchestrator at `/mcp`, and a narrow one-time `/enroll` endpoint. Enrollment is not a remote-control surface: it accepts only a short-lived pairing code created through authenticated MCP.

## Device identity lifecycle

Bootstrap devices may use `AURA_DEVICE_TOKENS`. Newly enrolled devices receive a random bearer credential. The relay persists only SHA-256 credential hashes plus revocation state in `AURA_DEVICE_REGISTRY_PATH` (default `./data/devices.json`). Legacy array-format registry files remain readable.

Authenticated MCP clients can use:

- `aura_create_pairing_code` — one-time 8-character code, valid for 5 minutes.
- `aura_rotate_device_credential` — rotate an enrolled credential; connected devices receive the replacement over their existing authenticated WSS session before reconnecting.
- `aura_revoke_device` / `aura_restore_device` — durable emergency lifecycle controls.
- `aura_devices` — inspect configured, revoked, and connected device IDs.

Android stores the issued credential with an Android Keystore-backed AES-GCM wrapper. AURA runtime memory remains device-local/offline and has no dependency on GitHub, Hjarni, Cortex, Dropbox, or other external memory services.

## Build and test

```bash
npm install
npm run typecheck
npm run test:registry
npm run start
```

## Relay database

The relay supports PostgreSQL persistence through `AURA_RELAY_DATABASE_URL` (or `DATABASE_URL`). It creates the `aura_relay_devices`, `aura_relay_events`, and `aura_relay_tasks` tables automatically on startup. Device bearer credentials remain outside the database in the existing registry; secrets are never written to PostgreSQL. Without a database URL, the existing bounded file-backed event journal remains the local fallback.
