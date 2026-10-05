# AURA Relay / MCP

This is the first real external relay for AURA. It has two authenticated surfaces:

- `/device` — outbound WebSocket endpoint for the Android AURA body.
- `/mcp` — remote MCP endpoint for an authorized MCP host such as ChatGPT developer mode.
- `/healthz` — liveness/status endpoint; no control access.

The Android client remains outbound-only. The relay never exposes a raw Android control port.

## Current flow

```text
Android notification / location / connectivity
        ↓
AURA Event Bus
        ↓
Durable Android event queue
        ↓
WSS /device
        ↓
AURA Relay
        ↓
Durable relay event journal
        ↓
MCP /mcp
        ↓
ChatGPT
        ↓
aura_send_task
        ↓
WSS /device
        ↓
A54 / Brain.run()
```

The Android queue keeps events until the relay acknowledges them. The relay persists accepted events to a bounded file-backed journal **before** sending the ACK. This means a relay restart does not erase the event history, and duplicate delivery is deduplicated by device ID + event ID.

Set `AURA_EVENT_STORE_PATH` to choose the journal path; the default is `./data/events.json`. The file is written with restrictive permissions and replaced atomically.

## Build and test

Node.js 20+ is required.

```bash
npm install
npm run typecheck
node smoke-test.mjs
npm start
```

The CI smoke test starts a real relay process and verifies:

1. authenticated AURA WebSocket connection,
2. event delivery and relay ACK,
3. durable event persistence,
4. event recovery after relay restart,
5. MCP authentication rejection without a token and acceptance with the configured token.

## MCP tools

- `aura_devices` — discover authorized/connected bodies.
- `aura_status` — inspect a connected body.
- `aura_events` — read recent persisted Android events.
- `aura_send_task` — send an authorized task to the Android body and wait for its correlated result.

High-impact Android actions remain subject to AURA's existing confirmation/safety path.

## ChatGPT integration boundary

A remote MCP server can be connected through supported ChatGPT custom MCP/app flows. Current OpenAI documentation states that full MCP apps are available on ChatGPT web for Business and Enterprise/Edu, while custom MCP apps are not available in the native ChatGPT mobile app. Therefore the relay is being built as a standards-based remote MCP service, but native-mobile unsolicited wake-up must not be assumed until the supported ChatGPT surface provides it.

Do not expose unauthenticated `ws://` or `/mcp` in production. Put the relay behind TLS/WSS and use separate MCP and per-device credentials.
