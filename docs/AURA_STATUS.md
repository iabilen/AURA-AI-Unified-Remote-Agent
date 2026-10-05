# AURA — Current Status & Roadmap

> Last updated: 2026-10-05 16:48 UTC / 2026-10-05 19:48 Türkiye (UTC+3)
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
- Notification → AURA → runtime → response
- Boot → service → bridge → relay
- Real CPU/RAM/temperature/battery measurements
- Fresh verification of the latest hardening changes in an APK

## Remaining roadmap
1. Code/source/dependency/permissions/architecture audit.
2. Focused hardening and regression tests; do not alter unrelated baseline failures.
3. Android 16/native/16 KB checks: llama.cpp/Gemma compatibility, RAM, inference latency, context, continuous runtime and 16 KB page-size compatibility.
4. Security review: key rotation, replay protection, command authorization, trusted-device policy and broader audit.
5. CI verification and documentation of known baseline failures; a narrowed focused suite is not the entire Android suite.
6. APK only after preceding gates pass and only when explicitly requested by the user.
7. A54 physical end-to-end test only after preceding gates.

## AI / agent work still to mature
- Validate local LLM on A54 for RAM, latency, context size and stability.
- Complete autonomous loop: observe → plan → choose tool/action → execute → evaluate → retry/continue.
- Complete advanced security hardening.

## Memory boundary
AURA runtime memory is standalone, offline and device-local. AURA must not depend on or contact GitHub, Hjarni, Cortex, Dropbox or other external memory/engineering services. Those systems may be used on the ChatGPT side for engineering continuity/backup; no reverse sync into AURA runtime.

## Execution order
**Code/audit → Tests → Android 16/native/16 KB → Security → CI → APK → A54 real-device test**

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

## Change log
- 2026-10-05 16:48 UTC / 19:48 Türkiye: canonical status/roadmap document created on the hardening branch.
- Future current-state changes should update this file rather than creating competing status files unless a historical snapshot is intentionally needed.

## Persistent instruction from user
- 2026-10-05 16:51 UTC / 2026-10-05 19:51 Türkiye (UTC+3): When the user says to “save” or “not forget” AURA-related information, decisions, plans, or status, update this canonical `docs/AURA_STATUS.md` file with the relevant item and a timestamp; do not leave it only in chat.

## Candidate architecture: Gemma + FunctionGemma action layer
- 2026-10-05 17:11 UTC / 2026-10-05 20:11 Türkiye (UTC+3): User approved and requested persistence of a new AURA architecture idea: keep **Gemma 4 E2B-it** as the primary local/general-purpose model and evaluate **FunctionGemma / Mobile Actions 270M** as a separate, lightweight action/function-routing layer beside it.
- Intended flow: Gemma handles language, reasoning and planning; the 270M action model maps suitable intents to structured tool/function calls; AURA's existing tool/execution layer performs the authorized Android action and returns the result to the main model.
- Candidate responsibilities: fast local action routing/function calling for operations such as notifications, app actions, connectivity/device controls and other explicitly exposed AURA tools.
- Status: **idea recorded, not implemented or integrated yet**. First validate Mobile Actions 270M on the A54 in AI Edge Gallery, then assess latency, RAM, heat, reliability and function-call quality before designing the AURA integration.
- This does **not** authorize an APK build or replacement. APK generation remains gated by an explicit user request.

## Mobile Actions / FunctionGemma A54 spike findings
- 2026-10-05 UTC+3: User tested Google AI Edge Gallery on the A54 with Gemma 4 E2B-it and Mobile Actions-270M.
- Gemma 4 E2B-it: good Turkish understanding and good use of the user-provided `otobiyografik.txt` context, but noticeably slower on-device.
- Mobile Actions-270M: action/function commands are executing successfully and the user confirmed that English commands and MacroDroid-style action names such as `turnOnFlashlight` work. Turkish natural-language commands are currently unreliable/not understood in the same way.
- Current interpretation: Mobile Actions should be treated as a specialized action/function interface, not as AURA's Turkish conversational brain. Google documents Mobile Actions as a FunctionGemma 270M fine-tune that maps natural-language commands to OS tool/app intents/function calls on-device.
- Architecture hypothesis is strengthened: Gemma 4 E2B-it should handle Turkish language, context, reasoning and planning; a lightweight action/function layer should translate/normalize the selected intent into AURA action names/tool calls; the AURA Tool/Execution Layer should remain responsible for authorization and actual Android execution.
- Important: The exact internal implementation of Google AI Edge Gallery's Mobile Actions execution bridge is not yet reverse-engineered. Treat the existence of an action/function-to-OS intent bridge as confirmed by Google's public description, but treat any deeper internal layering details as a hypothesis until source-level inspection or controlled tests establish them.
- No AURA code has been changed from this spike. No APK build/replacement is authorized.

## Revised AURA roadmap
1. **A54 model spike (current):** benchmark Gemma 4 E2B-it and Mobile Actions-270M on the real device for latency, RAM, heat, battery impact, context behavior and reliability.
2. **Action vocabulary discovery:** enumerate the Mobile Actions/function names that reliably execute, especially the action vocabulary already familiar from MacroDroid/Android automation. Record inputs, outputs, parameters and failure modes.
3. **Bridge analysis:** inspect public AI Edge Gallery / FunctionGemma documentation and, if needed later, source code to determine exactly where model output becomes an OS/app intent and what validation/execution layer sits between them. Do not copy Google's implementation blindly.
4. **AURA Action Contract:** design a stable internal schema for `intent -> action/function -> parameters -> authorization -> execution -> result -> verification`. Keep it independent from any one model.
5. **Bilingual boundary:** let Gemma own Turkish/multilingual understanding and planning; keep the action layer on a constrained, machine-readable vocabulary. If useful, add a deterministic alias/normalization layer so Turkish phrases map to canonical AURA actions without requiring the 270M model to become a Turkish chatbot.
6. **Tool/Execution integration:** connect the canonical action contract to AURA's existing Tool/Execution Layer, Event Queue/ACK and verification flow. Preserve at-least-once delivery and authorization boundaries.
7. **Native local inference spike:** evaluate the actual AURA integration path for Gemma/FunctionGemma (LiteRT/llama.cpp as appropriate), including Android 16 and 16 KB page-size requirements, without producing an APK yet.
8. **Security gate:** implement/verify per-action authorization, trusted-device policy, replay protection, key rotation and audit semantics before autonomous execution.
9. **A54 end-to-end validation:** only after code/tests/CI/audit gates pass, test boot -> service -> bridge -> model -> action -> Android -> verification -> response on the physical device.
10. **APK gate:** APK build/replacement remains last and only happens after explicit user request.

## Current decision
The two-model idea is now a **validated architectural direction / active spike**, not merely a speculative idea: the real-device test demonstrates a useful division of labor between a slower but capable Turkish/general model and a very small, fast action/function model. Integration is still explicitly deferred until the action vocabulary, bridge semantics and AURA action contract are understood.

## 2026-10-05 change log
- Added A54 Mobile Actions-270M test findings.
- Promoted Gemma 4 E2B-it + Mobile Actions/FunctionGemma from a generic candidate idea to an evidence-backed architectural direction, while keeping implementation deferred.
- Added action-vocabulary discovery, bridge analysis and model-independent AURA Action Contract as explicit roadmap gates.

