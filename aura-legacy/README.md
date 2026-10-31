# AURA Legacy — architecture workspace

This directory is a planning and architecture workspace for the current AURA Legacy workstream. The implementation is being added incrementally in the Android and Relay source trees; this README is not a claim that the modules below are all implemented or integrated.

## Principles

- ChatGPT through the OpenAI API is the primary and sole decision authority for connected decision tasks.
- AURA Legacy is the Android device agent and Body / Tool / Bridge layer.
- Device runtime memory stays app-private and offline.
- Risky actions pass through task-scoped authorization and required user approval.
- Actions are verified and their outcomes are reported.
- AURA Mix / V1 remains the protected historical baseline, not a target for in-place rewrites.

## Planned responsibility areas

- `core/` — runtime contracts and lifecycle
- `runtime/` — event processing and orchestration
- `memory/` — local continuity memory
- `eventbus/` — event bus, durable queue, ACK and retry
- `bridge/` — authenticated ChatGPT / Relay bridge
- `android/` — Android-specific event and action adapters
- `tests/` — unit, integration and device tests

The current concrete implementation and release gates are tracked in `docs/AURA_LEGACY_IMPLEMENTATION_AND_RELEASE_GATES.md`.
