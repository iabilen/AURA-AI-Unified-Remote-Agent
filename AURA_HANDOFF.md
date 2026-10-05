# AURA — Repeatable Build / Handoff Record

This file is the reproducible continuation record for Etap 2.

## Baseline
`iabilen/Ultra-Agent-A54chatgpt-source` at `fec145251a37d51700429104f4f4d5c1d1101e6d` is the verified working foundation.

## Verified build
AURA Build #2 succeeded at commit `e989c92f488a934de95091c6f5991bceca778e92`. Artifact: `aura-a54-debug`; SHA-256 `43885548732f76dfd69ae736993b2a5fc2b4f04ce3091e8d9bc5bc17ffe90ac2`.

## Toolchain
JDK 17, API 36, NDK 29.0.14206865, arm64-v8a, llama.cpp Android build, minSdk 28.

## Important history
- CMake pin removed so runner CMake is used.
- NDK pinned to 29.0.14206865.
- App minSdk aligned to 28.
- Incompatible AppFunctions bridge was removed; custom Task Bridge remains the intended integration path.
- Compose version-catalog accessors were corrected.

## Goal
`ChatGPT → secure Task Bridge → AURA → Brain.run() → Android` with event-first wakeups, camera/screen capabilities and device portability.

## Safety
Do not expose an unauthenticated remote-control endpoint. Preserve the existing gate/confirmation behavior for consequential actions.

## Bridge MVP — implemented
- AURA Event Bus: `AuraEvent` + `AuraEventBus` + `AuraCore`.
- Secure outbound transport: `AuraBridgeClient` uses `wss://` only and Bearer authentication; the phone does not open a listening remote-control socket.
- Runtime: `AuraRuntime` owns the device-neutral bridge/core layer and attaches to the existing `Brain`.
- Existing Brain safety/gate/tool path is preserved; remote tasks enter through `Brain.run()`.
- Bridge protocol v1 starts with `hello`, accepts `task`, and returns `result`.
- Bridge endpoint/token are deliberately not hard-coded. Configure later through `AuraRuntime.configureBridge(endpoint, token)`.

## Bridge hardening — current step
- Added a shared wire-protocol constant set (`AuraBridgeProtocol`) for task/event/status/ping/pong/result/error message types.
- Added automatic reconnect with bounded exponential backoff after transport failure.
- Added OkHttp WebSocket ping interval for liveness; relay-level `ping`/`pong` is also supported.
- Added explicit `status` and `status_request` messages with device ID and capabilities.
- Added protocol/device headers to the outbound WSS connection.
- Bridge logs never print the bearer token or task payload.
- `disconnect()` disables reconnect, while normal failures reconnect automatically.

## Next bridge steps
1. Relay-side request/result correlation with stable request IDs.
2. Device pairing + revoke instead of manually provisioning bearer tokens.
3. Persistent foreground wake service so the bridge survives normal UI lifecycle changes.
4. Event-source adapters (notifications/SMS/calls/calendar/battery).
5. Only after the device protocol is stable: real MCP Streamable HTTP relay and ChatGPT custom MCP app.

## Reproduction directive
Continue from the latest `main` commit. Do not replace the working Ultra Agent core. Keep A54 as the first body, not a hard-coded permanent device target. Keep the phone outbound-only and require authentication before any remote task reaches `Brain.run()`.
