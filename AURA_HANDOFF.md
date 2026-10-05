# AURA — Repeatable Build / Handoff Record

This file is the reproducible continuation record for Etap 2.

## Baseline
`iabilen/Ultra-Agent-A54chatgpt-source` at `fec145251a37d51700429104f4f4d5c1d1101e6d` is the verified working foundation.

## Architecture rule
**ChatGPT is the primary AI.** AURA is the Android body/agent layer. Gemma 3 1B is not the main decision engine; it remains only as an optional local fallback/continuity component.

Target:
`Android event → AURA → Relay/MCP → ChatGPT → decision → AURA → Brain.run() → Android`

## Verified build history
AURA Build #2 succeeded at commit `e989c92f488a934de95091c6f5991bceca778e92`. Artifact: `aura-a54-debug`; SHA-256 `43885548732f76dfd69ae736993b2a5fc2b4f04ce3091e8d9bc5bc17ffe90ac2`.

## Toolchain
JDK 17, API 36, NDK 29.0.14206865, arm64-v8a, llama.cpp Android build, minSdk 28.

## Bridge/device side — implemented
- `AuraEvent` + `AuraEventBus` + `AuraCore`.
- `AuraCore` now preserves the originating task/event ID into `Brain.run()` result correlation.
- `AuraBridgeClient` is outbound-only and uses `wss://` + bearer authentication.
- Automatic reconnect with bounded exponential backoff.
- OkHttp ping interval plus relay-level ping/pong.
- Protocol/device headers and status messages.
- Durable bounded event queue in SharedPreferences.
- Events are removed from the queue only after relay `ack`.
- Notification listener publishes normalized notification events without loading the local model for every notification.

## Relay/MCP — implemented
Commit `41c9ca80084f0744a536242fa81e952c406cf502` added:
- `relay/src/index.ts` real Node MCP server using the official v2 TypeScript MCP packages.
- `/mcp` remote MCP endpoint.
- `/device` authenticated WebSocket endpoint.
- Device authentication with per-device token map and bootstrap `*` fallback.
- `aura_devices`, `aura_status`, `aura_events`, `aura_send_task` tools.
- Stable task ID → device result correlation.
- Bounded in-memory event buffer.
- Independent relay typecheck workflow.

The current relay is intentionally stateless on the MCP HTTP side and keeps recent device events in memory. Production hardening will add durable server storage and pairing/revocation.

## Current event flow
```text
Android NotificationListener
        ↓
AURA Event Bus
        ↓
Durable queue (if relay unavailable)
        ↓ WSS /device
AURA Relay
        ↓ /mcp
ChatGPT custom MCP integration
        ↓ aura_send_task
AURA Relay
        ↓ WSS task
AURA Core → Brain.run()
        ↓
result(id=original task id)
        ↓
Relay → MCP caller
```

## Important integration boundary
A remote MCP server can be connected to supported ChatGPT developer-mode/custom-MCP flows, but an MCP endpoint by itself cannot force the native ChatGPT app to wake from an arbitrary Android notification. The relay now makes the event path real and durable; genuine unsolicited host-side wake/subscription must be implemented using whatever event/subscription mechanism the target ChatGPT surface supports.

## Security
- No unauthenticated remote-control port.
- Phone connects outbound only.
- WSS required by the Android bridge.
- Separate MCP and device bearer tokens.
- Constant-time token comparison on relay.
- Existing Android safety gate/confirmation remains in the execution path.
- Do not put secrets or notification payloads into logs.

## Next work, in order
1. Pairing + revoke + device identity lifecycle.
2. Foreground service / persistent bridge lifecycle with battery-aware behavior.
3. Event adapters: SMS, calls, calendar, battery, connectivity, boot.
4. Event filtering/privacy policy before forwarding sensitive content.
5. Camera/screen capture tools → ChatGPT vision path.
6. Durable relay storage and multi-device routing.
7. Verify genuine proactive ChatGPT event subscription/wake on the intended ChatGPT surface.

## Reproduction directive
Continue from latest `main`. Do not replace the working Ultra Agent core. Keep A54 as the first body, not a hard-coded permanent device target. Keep Android outbound-only and require authentication before any remote task reaches `Brain.run()`.
