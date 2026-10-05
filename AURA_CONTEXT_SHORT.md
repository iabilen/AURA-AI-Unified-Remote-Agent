# AURA Context — SHORT

> Canonical quick-start state for a new AURA development session.

## Project
- Name: AURA — AI Unified Remote Agent
- Repository: `iabilen/AURA-AI-Unified-Remote-Agent`
- Current base: `main`
- Architecture principle: **ChatGPT = primary AI / mind; AURA = Android body/agent layer.**
- Gemma 3 1B is optional local fallback/continuity support, not the primary decision engine.

## Verified current state
- AURA is based on the verified Ultra Agent foundation; the working core must remain intact.
- Android event → AURA Event Bus → relay/device WebSocket → relay MCP → ChatGPT custom MCP → `aura_send_task` → AURA Core `Brain.run()` is the intended active path.
- Bridge/device-side implementation exists and preserves originating task/event IDs through `Brain.run()` results.
- Relay MCP is implemented as a Node/TypeScript MCP HTTP endpoint; `/device` is an authenticated WebSocket endpoint.
- Device connectivity is outbound-only and authenticated with bearer tokens.
- Relay currently keeps recent device events in memory; durable server storage/pairing/revocation is a later hardening step.

## Runtime memory boundary — verified
- AURA continuity memory is **offline and device-local** in app-private storage.
- This local memory is the **only runtime memory source AURA depends on**.
- AURA does **not** contact GitHub, Hjarni, Cortex, Dropbox, or other external memory/backup systems.
- ChatGPT may consume continuity context through the normal AURA communication path and may back it up externally when useful.
- External backup is ChatGPT-side support; it is not automatic runtime sync into AURA.

## Immediate next priorities
1. Pairing/enrollment + device credential issuance/rotation.
2. Foreground/persistent bridge lifecycle with battery-aware behavior.
3. Event adapters (SMS, calls, calendar, battery, connectivity, boot).
4. Event filtering/privacy policy before forwarding sensitive content.
5. Camera/screen capture tools.
6. Durable relay storage and multi-device routing.
7. Verify genuine proactive ChatGPT event subscription/wake behavior on the intended ChatGPT surface.

## Continuity rule
When starting a new session, read this file first, then `AURA_MEMORY.md` and `AURA_EVENT_JOURNAL.md`, verify code state against GitHub `main`, and only then continue implementation.

## Source authority
1. **AURA local runtime memory** — sole AURA runtime continuity source.
2. **GitHub** — code, commits, branches, CI, and engineering snapshot/backup.
3. **Cortex** — ChatGPT-side durable project decisions/backup.
4. **Hjarni** — ChatGPT-side structured working notes/backup.
5. **Dropbox `z 01 ai-db`** — ChatGPT-side user-owned AI reference/archive.
