# AURA Memory — LONG

## Core principles
- AURA means **AI Unified Remote Agent**.
- ChatGPT remains the primary AI/orchestrator.
- AURA is the Android body/agent layer that observes events, transports them securely, executes authorized actions, and returns results.
- Gemma is optional local fallback/continuity support; it is not the main decision engine.
- AURA must be device-independent; A54 is the first endpoint, not a hard-coded permanent target.
- Remote control must remain authenticated, paired, and subject to the existing safety gate for consequential actions.
- Do not introduce NAS/F8/server dependencies into the core architecture.
- Do not require MacroDroid.
- Preserve the existing Ultra Agent working core until equivalence is proven by tests.

## Memory model
AURA has its own **offline, device-local continuity memory**. This is the only memory source AURA itself depends on at runtime.

- SHORT — current state and next action.
- LONG — durable architecture decisions and stable project knowledge.
- EVENT JOURNAL — chronological implementation events, decisions, tests, failures, and releases.

The mutable runtime copy lives in app-private Android storage and must remain usable without GitHub, Hjarni, Cortex, Dropbox, or any other external memory service. AURA must never contact those services to read, write, synchronize, or bootstrap runtime memory.

GitHub, Hjarni, Cortex, and other external stores are **ChatGPT-side engineering/backup resources only**. ChatGPT may consume AURA's locally exposed continuity context through the normal AURA communication path and may choose to back up relevant memory externally. External backups must never become a runtime dependency or automatic sync source for AURA.

## Current verified architecture
Android events wake a lightweight AURA layer. Meaningful events can be normalized, filtered, persisted in a bounded queue when needed, and forwarded through the authenticated outbound WebSocket device channel to the relay. The relay exposes MCP tools for device discovery/status/events/task dispatch. Task IDs are preserved through the bridge into `Brain.run()` and returned in results.

The relay MCP side is intentionally stateless regarding durable device identity for now. Recent device events are held in memory. Production hardening must add durable storage, explicit pairing/revocation, and multi-device routing without weakening authentication.

## Security invariants
- No unauthenticated remote-control port.
- Phone connection is outbound-only.
- Device WebSocket requires WSS and bearer authentication.
- MCP and device bearer tokens are separate.
- Relay performs constant-time token comparison.
- Secrets and notification payloads must never be written to logs.
- Existing Android safety confirmation remains in the execution path for consequential actions.

## New-session procedure
1. Read `AURA_CONTEXT_SHORT.md`.
2. Read the latest entries in `AURA_EVENT_JOURNAL.md`.
3. Treat the device-local AURA memory as the runtime continuity source.
4. Verify the current GitHub code/CI when implementation state matters; GitHub is an engineering source, not an AURA runtime dependency.
5. Consult Cortex/Hjarni continuity records only as ChatGPT-side backup/history when decisions or history matter.
6. State the exact last verified implementation point before changing code.
7. Implement the smallest testable next step.
8. Append the result to the event journal and update SHORT if the active state changed.
