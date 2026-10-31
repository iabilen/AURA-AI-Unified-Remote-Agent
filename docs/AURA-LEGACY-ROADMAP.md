# AURA Legacy — Technical Roadmap

Status: active architecture work in progress; not release-ready.

## Naming and lineage

- **AURA** is reserved as the umbrella product/brand name and is not assigned to this code branch yet.
- **AURA Mix** is the first usable generation based on the Ultra Agent fork. Its historical source remains on `main` and the frozen `v1-archive-2026-10-31` tag.
- **AURA Legacy** is the current independent-architecture workstream.
- The former development branch `develop/aura-v2` has been renamed to `develop/aura-legacy`; its commit history is unchanged.
- Historical V2 labels may remain in old commit messages, package paths, or prior notes. Do not mass-rename inherited source paths or package identifiers without a technical reason.

## Product boundary

- ChatGPT is the single decision authority and orchestrator for connected decision tasks, reached through the OpenAI API.
- Android AURA Legacy is the device-side Body / Tool / Bridge and secure execution layer.
- The old Ultra local Brain/model loop must not silently substitute for a missing ChatGPT decision.
- Runtime memory remains app-private and offline on the device.
- Risky actions require task-scoped authorization, actual capability/permission checks, and post-action verification.
- Relay provides authenticated transport and durable coordination.

## Work already added to the active branch

- Notification privacy policy and typed ActionPlan/action contracts.
- Encrypted app-private continuity memory.
- Durable encrypted Android event outbox and bounded inbound task inbox.
- Relay durable event journal, consumer lease/ACK/release/retention API, and durable task ledger.
- Remote task ingress isolated from the local Brain/model path; tasks are persisted and held until the authenticated ChatGPT decision path is ready.
- TaskApprovalGate primitive bound to task/action/idempotency key/expiry.

These are code additions, not proof of complete runtime integration or verified behavior.

## Remaining implementation phases

1. Authenticated, schema-validated ChatGPT decision gateway.
2. Replay-safe task dispatcher and device-side deduplication handshake.
3. Integrate TaskApprovalGate with actual approval UI and executor.
4. Capability registry from real OS/API/permission state.
5. Android action adapters and post-action verification.
6. Pairing, token rotation, revocation audit, graceful shutdown and stale-lock recovery.
7. Device-independent namespace/configuration and OEM/API capability adapters.
8. Bounded/redacted logs, metrics, and recovery paths.

## Verification gates

Build, unit/integration tests, lint/static analysis, protocol/security tests and Samsung Galaxy A54 physical-device validation remain pending. Do not call the work release-ready until those gates pass. APK/build, merge to `main`, release and external distribution require explicit user approval.

## Protected baseline

- `main` remains the AURA Mix/V1 historical baseline.
- Last recorded main SHA: `dad5e8137636dc56c6ceb85ad99376465de5bcc1`.
- Last recorded AURA Legacy workstream SHA before branch rename: `4c1229201144b691f05862ba7a827e78886c375d`.
- Do not merge or rewrite the baseline as part of this naming update.
