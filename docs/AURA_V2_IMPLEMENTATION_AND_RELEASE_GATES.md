# AURA V2 — implementation and release gates

This document is the authoritative engineering checklist for the V2 branch. It does not claim that unchecked work is implemented.

## Product boundary
- AURA is an on-device Android application; Relay is transport and durable coordination; ChatGPT is the sole policy/decision authority for connected decision tasks.
- Offline-capable local functions may run only within locally declared policy. No local model may silently replace a missing ChatGPT decision.
- Android capabilities are reported by the device and are never inferred from model output.

## Protocol v1 (current compatibility baseline)
- Every frame has `protocol: 1`; event/result frames carry stable `id`; task/result correlation uses the same task `id`.
- Event ACK means journal durable acceptance only.
- Task durable acceptance, device receipt, action completion and verified outcome are distinct states.
- Retry delivery is at-least-once. Device-side idempotency by task/action ID is a prerequisite before Relay may automatically replay dispatched work.
- Unknown protocol versions, malformed frames, expired plans and mismatched device/task IDs fail closed.

## Relay work
- [x] Durable inbound event journal; ACK only after durable acceptance.
- [x] Durable task ledger, bounded capacity, atomic replacement, schema validation, per-device idempotency key.
- [x] Single-writer lock; concurrent Relay startup fails closed.
- [x] Task state inspection through MCP.
- [ ] Event consumer lease/ACK/retention API. Pending events must never be pruned.
- [ ] Recoverable task dispatcher with device-side deduplication handshake.
- [ ] Persistent device pairing/token rotation and revocation audit.
- [ ] Authenticated, schema-validated decision gateway from ChatGPT to Android.
- [ ] Graceful shutdown and explicit lock release on signals; stale-lock operator recovery command.
- [ ] Bounded/redacted operational logs and metrics.

## Android work
- [x] Typed decision/action contracts.
- [x] Encrypted local durable event outbox.
- [x] Encrypted continuity memory store.
- [x] Notification privacy policy module.
- [ ] Integrate privacy policy before every event enters outbox.
- [ ] Integrate outbox with bridge: enqueue-before-send, ACK-by-event-ID, retry/recovery.
- [ ] Device-side durable task inbox and idempotency ledger.
- [ ] Task-scoped ActionGate with cancellation, expiry and approval binding.
- [ ] Capability registry based on actual OS/API/permission state.
- [ ] Action executor adapters and post-action verification.
- [ ] ChatGPT decision envelope validation and replay/expiry protection.
- [ ] Runtime lifecycle integration; no parallel local-model decision path.
- [ ] Device-independent namespace/configuration and OEM/API capability adapters.

## Required verification before calling V2 release-ready
1. Relay: install, typecheck, unit tests for journal/task ledger, crash/restart and lock contention tests.
2. Android: Gradle compile, unit tests, lint/static analysis.
3. Protocol integration: event ACK loss, duplicate event, duplicate task, result replay, reconnect, Relay restart, Android process death, full storage, corrupt files, expired action plan.
4. Security: token rotation/revocation, unauthorized device, malformed/oversized payload, sensitive notification redaction, approval spoof/replay.
5. Hardware: Samsung A54 baseline plus at least one other OEM/API level. Verify permission denial and offline behavior.
6. Only after explicit user approval: APK/build artifact, merge, release or external distribution.

## Known current limits
- The Relay task ledger persists state, but pending in-memory request promises do not survive restart. It is not yet a replay-safe dispatcher.
- The Android outbox/contracts/privacy/memory modules are not yet wired into the existing V1 runtime.
- Journal has no consumer lifecycle/retention yet; its configured capacity remains a hard backpressure boundary.
- No compile, tests, static analysis, runtime test or device test has been run for the new Relay task-store commit.
