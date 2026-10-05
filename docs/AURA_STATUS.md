# AURA — Current Status & Roadmap

> Last updated: 2026-10-05 19:33 UTC / 2026-10-05 22:33 Türkiye (UTC+3)
> Branch: `aura/pre-device-hardening-plan`
> Rule: No APK build/replacement unless explicitly requested by the user.

## Current capabilities
- Android AURA Core / AuraRuntime
- Brain / AI orchestration
- Device-local offline continuity memory
- Event Bus / system event bridge
- At-least-once Event Queue with ACK, in-flight tracking, reconnect resend and coalescing
- Android ↔ Relay WebSocket bridge
- Relay registry, task/event system and PostgreSQL DB
- Authentication and pairing/enrollment
- Notification Listener and scam warning

## Completed hardening
- Relay SIGTERM/SIGINT deterministic idempotent shutdown; pending tasks, WebSocket, HTTP and DB are cleaned up.
- Android foreground bridge migrated from `dataSync` to `connectedDevice`; connected-device FGS permission is used; no fake `onTimeout()` workaround.
- Notification title/text/payload are excluded from persistent operational/scam logs; runtime may use content transiently.

## Not yet physically validated
- Full A54 end-to-end test
- Real Android 15/16 device behavior
- Long-lived WebSocket/reconnect on A54
- Notification → AURA → runtime → response path
- Boot → service → bridge → relay
- Real CPU/RAM/temperature/battery measurements
- Fresh verification of the latest hardening changes in an APK

## Remaining infrastructure roadmap
1. Code/source/dependency/permissions/architecture audit.
2. Focused hardening and regression tests; do not alter unrelated baseline failures.
3. Android 16/native/16 KB checks: Android runtime compatibility, app/dependency alignment, RAM, continuous runtime and 16 KB page-size compatibility.
4. Security review: key rotation, replay protection, command authorization, trusted-device policy and broader audit.
5. CI verification and documentation of known baseline failures; a narrowed focused suite is not the entire Android suite.
6. APK only after preceding gates pass and only when explicitly requested by the user.
7. A54 physical end-to-end test only after preceding gates.

## AI / agent work still to mature
- Validate the single Brain + OpenAI provider path for latency, context handling, cancellation, network failure and stability.
- Complete autonomous loop: observe → understand → plan → choose action → authorize → execute → verify → respond/continue.
- Complete advanced security hardening.

## Memory boundary
AURA runtime memory is standalone, offline and device-local. AURA must not depend on or contact GitHub, Hjarni, Cortex, Dropbox, ChatGPT or other external memory/engineering services. Those systems may be used on the ChatGPT side for engineering continuity/backup only; there is no reverse sync into the AURA runtime.

## Execution order
**Code/audit → tests → Android 16/native/16 KB → security → CI → A54 real-device test → APK gate.**

## Critical rules
- Never build or replace an APK without explicit user request.
- Do not merge hardening work into `main` before agreed gates.
- Do not physically test A54 before code/tests/CI/audit gates are ready.
- Preserve Event Queue/ACK unless tests prove a change is required.
- No unrelated redesign.

## Rough maturity indicators
- Infrastructure: ~85%
- AI/agent: ~60%

These are internal maturity indicators, not project completion percentages.

## Superseded local-model exploration

An earlier A54 exploration evaluated on-device model options and a possible two-model action pipeline. That direction was explicitly rejected in favor of the original single-Brain architecture. The exploration is retained only as historical context; it is not part of the current runtime design or roadmap.

No local model is required by the current AURA runtime. The active architecture is the single Brain + OpenAI/ChatGPT path recorded below.

## 2026-10-05 decision: single Brain + OpenAI/ChatGPT

The user approved simplifying AURA around the original Brain and explicitly requested that the OpenAI/ChatGPT integration be included as the single external AI intelligence path. Gemma and FunctionGemma/Mobile Actions are no longer part of the intended AURA runtime architecture.

### Design
- Preserve `AuraRuntime → Brain → Tools → ActionGate → Android → Verification → Event Queue/ACK`.
- Brain remains the central orchestration component; do not rewrite it into multiple new AI/agent layers.
- Remove Gemma/local-LLM/FunctionGemma infrastructure only after a dependency audit proves each item is no longer required.
- Integrate OpenAI/ChatGPT at the smallest practical Brain-facing boundary. Provider-specific code must not spread into Tools, Android services, Gate or Verification.
- OpenAI/ChatGPT may reason, interpret context and propose tool/action work; existing authorization, execution and verification remain authoritative.
- AURA persistent runtime memory remains device-local/offline. ChatGPT is not AURA memory.
- API credentials must never be committed or packaged into the APK.
- Network/API failure must fail safely and must never bypass authorization or claim an action succeeded without verification.

### Implementation plan
1. Inventory all Gemma, FunctionGemma/Mobile Actions, `LocalModelEngine`, llama.cpp/native inference, model presets and model-only dependencies.
2. Map their consumers and classify each as removable, replaceable or still required.
3. Audit the current Brain call path and identify the minimum provider integration point.
4. Check current official OpenAI API documentation and select the smallest suitable API boundary for Brain.
5. Define credential storage/provisioning, timeout/cancellation, network failure, privacy/logging and authorization handoff before live integration.
6. Remove dead local-AI infrastructure in the smallest safe changeset.
7. Connect the existing Brain to OpenAI/ChatGPT without redesigning the rest of AURA.
8. Add focused tests for provider success/failure, malformed output, timeout/cancellation, offline behavior, authorization rejection and verification failure.
9. Re-scan for dead Gemma/model references, secrets and accidental changes to Queue/ACK, Gate, Tools and Verification.
10. Run CI/audit gates. No APK build at this stage.
11. Only after gates pass, and only if the user explicitly asks, produce an APK and later perform A54 physical validation.

### Acceptance criteria
- No Gemma or FunctionGemma model is required for normal AURA operation.
- No unnecessary local LLM runtime remains after dependency audit.
- Brain remains recognizable and behaviorally central.
- OpenAI/ChatGPT is the single external AI intelligence path.
- Tools, ActionGate, Android execution, Verification and Event Queue/ACK remain authoritative.
- No API secret reaches GitHub or the APK.
- Provider/network failures are explicit and safe.
- Changed-path tests pass and unrelated baseline failures remain documented.
- No APK is built/replaced without explicit user request.

### Process state
The approved single-Brain + OpenAI/ChatGPT plan has been implemented on `aura/pre-device-hardening-plan`. Brain remains the central orchestrator; Gemma/FunctionGemma and the local llama.cpp runtime path were removed. OpenAI is now the single external AI provider boundary.

Verification: the test-only Android CI run `37358547424` passed after the final parser-fixture correction. No APK was assembled or uploaded. Live OpenAI credential/API validation and A54 physical end-to-end validation remain intentionally pending.

Implementation remains on `aura/pre-device-hardening-plan`; `main` was not changed.

## Change log
- 2026-10-05 18:24 UTC / 21:24 Türkiye: user approved the single-Brain direction, requested removal of Gemma and integration of OpenAI/ChatGPT as the single external AI path, and asked for the plan to be recorded.

- 2026-10-05 18:31 UTC / 21:31 Türkiye: detailed implementation plan created at `docs/superpowers/plans/2026-10-05-single-brain-openai.md` after user approval. Plan preserves Brain, removes Gemma/local inference, integrates OpenAI at the existing provider boundary, and keeps APK generation gated.

- 2026-10-05 18:49 UTC / 21:49 Türkiye: single-Brain + OpenAI implementation verified by test-only CI; no APK generated. Gemma/local inference runtime removed; live API and physical A54 validation remain pending.
