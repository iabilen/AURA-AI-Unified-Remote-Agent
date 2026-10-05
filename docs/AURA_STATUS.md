# AURA — Current Status & Roadmap

> Last updated: 2026-10-05 18:05 UTC / 2026-10-05 21:05 Türkiye (UTC+3)
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
3. Android 16/native/16 KB checks: llama.cpp/Gemma compatibility, RAM, inference latency, context, continuous runtime and 16 KB page-size compatibility.
4. Security review: key rotation, replay protection, command authorization, trusted-device policy and broader audit.
5. CI verification and documentation of known baseline failures; a narrowed focused suite is not the entire Android suite.
6. APK only after preceding gates pass and only when explicitly requested by the user.
7. A54 physical end-to-end test only after preceding gates.

## AI / agent work still to mature
- Validate local LLM behavior on A54 for RAM, latency, context size and stability.
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

## Candidate architecture: Gemma + action layer → revised direction
The A54 spike demonstrated a useful division of labor between a capable general/local model and a very small action/function model. However, the original two-model architecture is **not mandatory**.

The current preferred direction is:

```text
USER
  ↓
AURA Intent / Command Router
  ├── Simple / known action
  │      ↓
  │   Canonical AURA Action
  │
  └── Complex / ambiguous request
         ↓
      Gemma 4 E2B-it
         ↓
    Canonical AURA Action
         ↓
     Authorization
         ↓
    Tool / Execution Layer
         ↓
       Android
         ↓
     Verification
```

### Architecture decisions
- **270M FunctionGemma / Mobile Actions is optional, not mandatory.** AURA must not depend on it for normal operation.
- **Gemma 4 E2B-it remains the candidate primary local/general model**, but it should be invoked selectively for complex, ambiguous, contextual or planning-heavy requests rather than every simple command.
- **Deterministic Intent / Command Router is introduced conceptually before any LLM.** Simple, explicitly supported commands should resolve directly to canonical actions without model inference.
- **AURA Action Contract is model-independent.** It is the stable boundary between intent interpretation and authorization/execution.
- FunctionGemma/Mobile Actions 270M may later be plugged in as an alternative action engine if controlled benchmarks demonstrate a real benefit in speed, accuracy, battery use or coverage.
- The Tool/Execution layer remains responsible for authorization and actual Android execution; models do not bypass it.
- No model-specific output format should become the internal AURA contract.

### Why this direction
The A54 spike showed that Gemma 4 E2B-it is useful for Turkish understanding and user-provided context, but noticeably slower on-device, while Mobile Actions 270M can execute specialized action/function commands but is currently unreliable for Turkish natural-language commands. This supports selective model invocation and a constrained canonical action vocabulary rather than making a 270M model a required conversational layer.

Google's current public Mobile Actions documentation describes FunctionGemma as a specialized 270M function-calling model that maps natural-language commands to OS tools/app intents on-device. That validates the role of a small action model, but does not require AURA to adopt Google's model as a mandatory architectural component.

## A54 Mobile Actions / FunctionGemma spike findings
- 2026-10-05 UTC+3: User tested Google AI Edge Gallery on the A54 with Gemma 4 E2B-it and Mobile Actions-270M.
- Gemma 4 E2B-it: good Turkish understanding and good use of user-provided `otobiyografik.txt` context, but noticeably slower on-device.
- Mobile Actions-270M: action/function commands execute successfully; user confirmed English commands and MacroDroid-style action names such as `turnOnFlashlight` work. Turkish natural-language commands are currently unreliable/not understood in the same way.
- Current interpretation: Mobile Actions should be treated as a specialized action/function interface, not as AURA's Turkish conversational brain.
- The exact internal implementation of Google AI Edge Gallery's execution bridge is not reverse-engineered. Public Google material establishes the model → function-call → application/OS execution pattern; deeper internal layering remains a hypothesis until source-level inspection or controlled tests establish it.
- No AURA code has been changed from this spike. No APK build/replacement is authorized.

## Revised AURA AI/action roadmap
1. **A54 model spike (current):** benchmark Gemma 4 E2B-it and Mobile Actions-270M for latency, RAM, heat, battery impact, context behavior and reliability.
2. **Action vocabulary discovery:** enumerate actions/functions that can reliably execute and record inputs, outputs, parameters and failure modes.
3. **Intent Router design:** define which commands can be resolved deterministically and which require model interpretation/planning.
4. **AURA Action Contract:** define `intent → action/function → parameters → authorization → execution → result → verification`, independent of any model.
5. **Bilingual boundary:** allow Gemma to own Turkish/multilingual understanding and planning; keep execution-facing action names constrained and machine-readable. Add deterministic aliases/normalization where useful.
6. **Tool/Execution integration:** connect the canonical action contract to AURA's existing Tool/Execution Layer, Event Queue/ACK and verification flow. Preserve at-least-once delivery and authorization boundaries.
7. **Optional action-engine spike:** evaluate 270M FunctionGemma/Mobile Actions only as a plugin/alternative against the same AURA Action Contract. Remove it from the critical path if it provides no measurable benefit.
8. **Native local inference spike:** evaluate the actual AURA integration path for Gemma and optional action models (LiteRT/llama.cpp as appropriate), including Android 16 and 16 KB page-size requirements, without producing an APK yet.
9. **Security gate:** verify per-action authorization, trusted-device policy, replay protection, key rotation and audit semantics before autonomous execution.
10. **A54 end-to-end validation:** only after code/tests/CI/audit gates pass, test boot → service → bridge → router/model → action → Android → verification → response on the physical device.
11. **APK gate:** APK build/replacement remains last and requires explicit user request.

## Current decision
The architecture has now been **deliberately simplified** from a mandatory two-model pipeline to a model-independent action architecture:

- Simple deterministic commands should not require Gemma or FunctionGemma.
- Complex requests may use Gemma 4 E2B-it to understand, reason and plan.
- FunctionGemma/Mobile Actions 270M is an optional specialized action engine, not a required AURA component.
- The AURA Action Contract and Tool/Execution Layer are the stable core.
- Integration is still deferred until the action vocabulary, contract, bridge semantics and A54/native inference constraints are validated.

## Change log
- 2026-10-05 16:48 UTC / 19:48 Türkiye: canonical status/roadmap document created on the hardening branch.
- Added persistent instruction: when the user says “save” or “not forget” about AURA-related information, decisions, plans or status, update this canonical file with the relevant item and timestamp.
- Added A54 Mobile Actions-270M test findings.
- Promoted Gemma 4 E2B-it + Mobile Actions/FunctionGemma from a generic candidate idea to an evidence-backed architectural direction, while keeping implementation deferred.
- Added action-vocabulary discovery, bridge analysis and model-independent AURA Action Contract as explicit roadmap gates.
- 2026-10-05 18:05 UTC / 21:05 Türkiye: revised the architecture so the deterministic Intent/Command Router precedes model inference, Gemma is selective rather than mandatory for simple commands, and FunctionGemma/Mobile Actions 270M is optional rather than a required second model.

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
This is the written design/spec requested by the user. Implementation has not started. The next formal gate is review/approval of this written spec; after approval, create the detailed implementation plan and only then select the execution method before code changes.

## Change log
- 2026-10-05 18:24 UTC / 21:24 Türkiye: user approved the single-Brain direction, requested removal of Gemma and integration of OpenAI/ChatGPT as the single external AI path, and asked for the plan to be recorded.
