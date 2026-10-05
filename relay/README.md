# AURA Relay / MCP

This is the first real external relay for AURA. It has two authenticated surfaces:

- `/device` — outbound WebSocket endpoint for the Android AURA body.
- `/mcp` — remote MCP endpoint for an authorized MCP host such as ChatGPT developer mode.
- `/healthz` — liveness only.

The Android client remains outbound-only. The relay never exposes a raw Android control port.

## Current flow

```text
Android notification
      ↓
AURA NotificationListener
      ↓
AURA Event Bus
      ↓
Durable event queue
      ↓ WSS
AURA Relay
      ↓ MCP
ChatGPT
      ↓ aura_send_task
AURA Relay
      ↓ WSS
A54 / Brain.run()
```

The relay keeps a bounded in-memory event buffer and correlates task IDs with result IDs. The Android queue retries events after reconnect and removes them only after relay acknowledgement.

## Run

Node.js 20+ is required.

```bash
npm install
cp .env.example .env
# export the values from .env in your deployment environment
npm run typecheck
npm start
```

The phone must be configured with the relay's **WSS** URL, for example:

`wss://your-host.example/device`

The MCP host uses:

`https://your-host.example/mcp`

Do not expose plain `ws://` or unauthenticated `/mcp` in production. Put the service behind TLS/reverse proxy or configure HTTPS directly.

## MCP tools

- `aura_devices` — discover authorized/connected bodies.
- `aura_status` — inspect a connected body.
- `aura_events` — read recent Android events, including notifications.
- `aura_send_task` — send a task to the Android body and wait for its correlated result.

## Important ChatGPT integration boundary

A remote MCP server can be connected to supported ChatGPT developer-mode/custom-MCP flows, but the MCP server itself does not magically force the native ChatGPT app to wake on an arbitrary Android event. The relay therefore makes events durable and queryable now; unsolicited host-side wake/subscription behavior is a separate integration capability and must be verified on the target ChatGPT surface.

## Security

- MCP bearer token and device bearer tokens are separate.
- Tokens are compared with constant-time comparison.
- Device authentication happens during the WebSocket upgrade.
- Device IDs are explicit; production should use per-device tokens rather than the bootstrap `*` mapping.
- High-impact Android actions still pass through AURA's existing safety/confirmation path.
- No notification content is logged by the relay code itself.

Pairing/revocation and durable server-side event storage are the next relay hardening steps.
