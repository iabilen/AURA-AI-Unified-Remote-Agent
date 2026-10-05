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
