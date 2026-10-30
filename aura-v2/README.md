# AURA V2

This directory is the isolated home for the next AURA architecture. It is intentionally scaffold-only at this stage; no V1 implementation has been copied, moved, or replaced.

## Principles

- ChatGPT is the primary AI brain and orchestrator.
- AURA is the Android device agent and Body / Tool / Bridge layer.
- Device runtime memory stays app-private and works offline.
- Risky actions pass through ActionGate and required user approval.
- Actions are verified and their outcomes are reported.
- Existing V1 code is reference material, not a target for in-place rewrites.

## Planned modules

- `core/` — runtime contracts and lifecycle
- `runtime/` — event processing and orchestration
- `memory/` — local continuity memory
- `eventbus/` — event bus, durable queue, ACK and retry
- `bridge/` — authenticated ChatGPT / relay bridge
- `android/` — Android-specific event and action adapters
- `tests/` — unit, integration and device tests

Modules will be added only after the V1 code audit identifies what can be reused safely.
