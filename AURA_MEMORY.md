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
AURA continuity is intentionally external to any single ChatGPT conversation:

- SHORT — current state and next action.
- LONG — durable architecture decisions and stable project knowledge.
- EVENT JOURNAL — chronological implementation events, decisions, tests, failures, and releases.

The files in this repository are the Git-tracked canonical project-memory layer. Runtime/device-local memory may later mirror the relevant subset for offline continuity.

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
3. Verify the current GitHub `main` HEAD and relevant files.
4. Compare against Cortex/Hjarni continuity records when decisions or history matter.
5. State the exact last verified implementation point before changing code.
6. Implement the smallest testable next step.
7. Append the result to the event journal and update SHORT if the active state changed.
