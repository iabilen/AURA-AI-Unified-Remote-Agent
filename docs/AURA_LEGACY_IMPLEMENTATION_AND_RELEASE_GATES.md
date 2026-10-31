# AURA Legacy — implementation and release gates

This is the engineering checklist for the AURA Legacy workstream (formerly developed under the internal V2 label). It records implementation status, not a claim that unchecked or untested work is complete.

## Naming and lineage

- AURA is reserved as the umbrella brand/product name.
- AURA Mix is the first usable Ultra Agent-derived generation, retained as the historical `main` / V1 baseline.
- AURA Legacy is the current independent-architecture workstream on `develop/aura-legacy`.
- Internal source/package paths may still contain historical `ultra` or `v2` names. They are not product names and will only be changed when a scoped technical migration justifies it.

## Product boundary

- AURA Legacy is an on-device Android application; Relay is transport and durable coordination; ChatGPT via the OpenAI API is the sole policy/decision authority for connected decision tasks.
- Offline-capable local functions may run only within locally declared policy. No local model may silently replace a missing ChatGPT decision.
- Android capabilities are reported by the device and are never inferred from model output.
- Runtime memory is app-private and device-local; COM/Notion/Cortex/Dropbox are not runtime dependencies.

## Protocol v1 (compatibility baseline)

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
- [x] Event consumer lease/ACK/retention API. Claims and leases are persisted; pending events are never pruned; only consumed records older than a minimum 24-hour retention may be removed.
- [ ] Recoverable task dispatcher with device-side deduplication handshake.
- [ ] Persistent device pairing/token rotation and revocation audit.
- [ ] Authenticated, schema-validated ChatGPT decision gateway to Android.
- [ ] Graceful shutdown and explicit lock release on signals; stale-lock operator recovery command.
- [ ] Bounded/redacted operational logs and metrics.

## Android work

- [x] Typed decision/action contracts.
- [x] Encrypted local durable event outbox.
- [x] Encrypted continuity memory store.
- [x] Notification privacy policy module.
- [x] Notification privacy policy is integrated before event creation; notification text/title remain local and are not exported.
- [x] Encrypted outbox is integrated with bridge: enqueue-before-send, ACK-by-event-ID, persisted leases and reconnect/process-death recovery.
- [x] Encrypted bounded device-side task inbox with stable task ID, payload hash, duplicate detection, and crash-safe state recovery.
- [x] Remote task ingress is isolated from the local Brain/model path; tasks are persisted but not executed without an authenticated ChatGPT decision.
- [x] TaskApprovalGate primitive added, bound to task/action/idempotency key/expiry.
- [ ] Integrate task-scoped approval with the real approval UI and executor.
- [ ] Capability registry based on actual OS/API/permission state.
- [ ] Action executor adapters and post-action verification.
- [ ] ChatGPT decision envelope validation and replay/expiry protection.
- [ ] Complete authenticated ChatGPT decision gateway and safe task dispatch handshake.
- [ ] Runtime lifecycle integration beyond durable receipt; no parallel local-model decision path.
- [ ] Device-independent namespace/configuration and OEM/API capability adapters.

## Required verification before calling AURA Legacy release-ready

1. Relay: install, typecheck, unit tests for journal/task ledger, crash/restart and lock contention tests.
2. Android: Gradle compile, unit tests, lint/static analysis.
3. Protocol integration: event ACK loss, duplicate event, duplicate task, result replay, reconnect, Relay restart, Android process death, full storage, corrupt files, expired action plan.
4. Security: token rotation/revocation, unauthorized device, malformed/oversized payload, sensitive notification redaction, approval spoof/replay.
5. Hardware: Samsung A54 baseline plus at least one other OEM/API level. Verify permission denial and offline behavior.
6. Only after explicit user approval: APK/build artifact, merge, release or external distribution.

## Known current limits

- The Relay task ledger persists state, but pending in-memory request promises do not survive restart. It is not yet a replay-safe dispatcher.
- The Android encrypted outbox is wired into bridge transport; notification export is metadata-only behind the existing capture preference.
- Android task ingress persists bounded AES-GCM encrypted records using an Android Keystore key. Same-ID/same-payload deliveries are deduplicated; same-ID/different-payload deliveries fail closed. A RUNNING task discovered after restart becomes UNKNOWN_AFTER_CRASH and is never auto-replayed.
- Remote tasks no longer enter the legacy local Brain/model loop. They remain stored in RECEIVED state until the authenticated ChatGPT decision gateway and task-scoped authorization/dispatch path are implemented.
- The typed action contracts and continuity memory are not yet fully wired into the action runtime.
- Journal consumer lifecycle is available through authenticated MCP tools. Its configured capacity remains a hard backpressure boundary; retention cleanup is explicit, not automatic.
- The latest recorded integration slice has not been compiled or tested. No Gradle build, unit tests, lint/static analysis, runtime test or physical-device test has been run. Verification remains deferred until implementation work is complete.

## Implementation log

- Android bridge persists outbound event/result frames in the encrypted outbox before WebSocket send. It removes records only after matching Relay ACK IDs and uses durable leases plus reconnect recovery to retry unacknowledged sends (at-least-once; not exactly-once).
- Notification event export is gated by the existing `captureNotifications` preference and sends package/category/time/ongoing metadata only. Title/body remain local for existing on-device triggers and optional local logging.
- Relay exposes `aura_claim_events`, `aura_ack_event`, `aura_release_event`, and `aura_prune_consumed_events`. Lease claims are persisted before delivery; ACK is bound to consumer/device/event IDs and an unexpired lease; pruning removes only consumed rows after at least 24 hours.
- Android runtime persists remote task frames before reporting local receipt and never routes those tasks to the local Brain. No tests or APK/build actions were run.
