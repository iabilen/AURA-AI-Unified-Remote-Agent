# AURA Event Journal

Chronological, append-only project continuity log. Keep entries concise and factual. Never store secrets, bearer tokens, notification payloads, or personal sensitive data here.

## 2026-10-05 — Continuity Core initialized
- Verified repository: `iabilen/AURA-AI-Unified-Remote-Agent`.
- Verified `main` HEAD: `dad5e8137636dc56c6ceb85ad99376465de5bcc1`.
- Existing `AURA_HANDOFF.md` is the current technical handoff authority and records the implemented bridge/relay path.
- Existing relay implementation includes Node/TypeScript MCP, authenticated `/device` WebSocket, device token mapping, task/result correlation, bounded in-memory event buffering, and relay typecheck workflow.
- Created the first Git-tracked continuity layer: `AURA_CONTEXT_SHORT.md`, `AURA_MEMORY.md`, and this journal.
- Next implementation focus remains device pairing/revocation/identity lifecycle, followed by persistent bridge lifecycle and event adapters.

## 2026-10-05 — Local-only runtime memory boundary
- Confirmed AURA runtime continuity is device-local and app-private.
- AURA must not contact GitHub, Hjarni, Cortex, Dropbox, or other external memory/backup services for runtime memory.
- GitHub/Hjarni/Cortex are ChatGPT-side engineering/backup resources only; they are not runtime dependencies or automatic sync inputs for AURA.
- ChatGPT may consume AURA locally exposed continuity context through the normal AURA communication path and may back up relevant state externally.

## 2026-10-05 — Durable device revocation lifecycle
- Added persistent relay-side revocation state keyed by device ID.
- Bearer tokens remain deployment configuration and are never written to the registry file.
- Added authenticated MCP controls to revoke/restore configured devices; revocation closes active sessions and survives relay restart.
- Added registry regression tests and updated relay documentation.
- Pairing/enrollment and credential issuance/rotation remain the next device-identity step.
