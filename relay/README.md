# AURA Relay / MCP

The relay has two authenticated surfaces:

- `/device` — outbound WebSocket endpoint for the Android AURA body.
- `/mcp` — authenticated MCP endpoint for the authorized AI/orchestrator.
- `/healthz` — liveness/status only; it exposes no control path.

## Device identity lifecycle

`AURA_DEVICE_TOKENS` supplies the configured per-device bearer credentials. The relay keeps **revocation state** separately in `AURA_DEVICE_REGISTRY_PATH` (default `./data/devices.json`). The registry persists only revoked device IDs; bearer tokens are never written to that file.

Authenticated MCP clients can use:

- `aura_revoke_device` — persist a device revocation and immediately close its active WebSocket.
- `aura_restore_device` — remove a persisted revocation for a configured device.
- `aura_devices` — inspect configured, revoked, and connected device IDs.

A revoked device is rejected during the WebSocket upgrade even after a relay restart. Pairing/enrollment (creating and delivering a new credential) remains a separate next step; this change establishes the durable revoke boundary first.

## Runtime memory boundary

AURA Android continuity memory is device-local/offline. The relay does not read or write GitHub, Hjarni, Cortex, Dropbox, or other external memory services. Those remain ChatGPT-side backup/engineering resources only.

## Build and test

```bash
npm install
npm run typecheck
npm run test:registry
npm run start
```
