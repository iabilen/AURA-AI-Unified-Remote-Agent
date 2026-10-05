# AURA Event Journal

Chronological, append-only project continuity log. Keep entries concise and factual. Never store secrets, bearer tokens, notification payloads, or personal sensitive data here.

## 2026-10-05 — Continuity Core initialized
- Verified repository: `iabilen/AURA-AI-Unified-Remote-Agent`.
- Verified `main` HEAD: `dad5e8137636dc56c6ceb85ad99376465de5bcc1`.
- Existing `AURA_HANDOFF.md` is the current technical handoff authority and records the implemented bridge/relay path.
- Existing relay implementation includes Node/TypeScript MCP, authenticated `/device` WebSocket, device token mapping, task/result correlation, bounded in-memory event buffering, and relay typecheck workflow.
- Created the first Git-tracked continuity layer: `AURA_CONTEXT_SHORT.md`, `AURA_MEMORY.md`, and this journal.
- Next implementation focus remained device pairing/revocation/identity lifecycle, followed by persistent bridge lifecycle and event adapters.

## 2026-10-05 — Local-only runtime memory boundary
- Confirmed AURA runtime continuity is device-local and app-private.
- AURA must not contact GitHub, Hjarni, Cortex, Dropbox, or other external memory/backup services for runtime memory.
- GitHub/Hjarni/Cortex are ChatGPT-side engineering/backup resources only; they are not runtime dependencies or automatic sync inputs for AURA.
- ChatGPT may consume AURA-local continuity context through the normal AURA communication path and may back up relevant state externally.

## 2026-10-05 — Durable device revocation lifecycle
- Added persistent relay-side revocation state keyed by device ID.
- Bearer tokens remain deployment configuration and are never written to the revocation registry.
- Added authenticated revoke/restore controls; revocation closes active device sessions and survives relay restart.
- Added registry regression coverage and relay documentation.
- Pairing/enrollment and credential issuance/rotation were subsequently added as the device identity lifecycle foundation.

## 2026-10-05 — Persistent Android bridge lifecycle
- Started AURA runtime from the existing foreground service lifecycle.
- Added boot/package-replacement restart handling for the Android agent service.
- Preserved the existing outbound-only WSS bridge and local durable event queue.
- No external memory dependency was introduced.

## 2026-10-05 — Privacy-safe Android system event bridge
- Added a dynamic system-event bridge for battery, power, screen, headset, package, and connectivity-related state.
- Forwarded only low-risk operational metadata; no message bodies, notification text, contacts, or credentials are included.
- System event bridge starts with the AURA runtime and remains device-local until normal authenticated event forwarding.

## 2026-10-05 — Durable Android event queue hardening
- Kept event delivery ACK-driven: a locally queued event is removed only after an explicit relay ACK for its event ID.
- Added priority-aware bounded retention so higher-priority events survive queue pressure over lower-priority stale events.
- Added narrow coalescing for noisy battery/power/screen/connectivity state, while preserving distinct event IDs for non-coalesced events.
- Queue flush now sends highest-priority pending events first after reconnect; unacknowledged events remain durable and can be resent safely after a disconnect.
- The runtime remains local-only; no external memory or backup service was added.

## 2026-10-05 — Strict ACK durability and persistence
- Closed a delivery gap where an event could be sent successfully at the WebSocket layer but never reach the durable queue, so a lost ACK could cause permanent event loss.
- Android now persists every outbound event before attempting immediate WebSocket delivery; only an explicit relay ACK removes it.
- Queue writes now use synchronous persistence for the bounded queue, ensuring enqueue/ACK state reaches storage before the operation returns.
- This establishes at-least-once event delivery semantics across reconnects/process death; relay-side event IDs remain the deduplication key.
- No external memory, backup, or runtime dependency was introduced.
