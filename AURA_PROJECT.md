# AURA Project Handoff

## Stable baseline
- Repository: iabilen/Ultra-Agent-A54chatgpt-source
- Baseline: fec145251a37d51700429104f4f4d5c1d1101e6d
- AURA repository: iabilen/AURA-AI-Unified-Remote-Agent
- Stage: Etap 2 foundation

## Non-negotiables
1. Keep the working Ultra Agent core intact.
2. Do not introduce NAS/F8/server dependencies.
3. Do not require MacroDroid.
4. AURA must be device-independent; A54 is the first endpoint.
5. Remote control must be authenticated and paired.
6. Keep the existing safety gate for consequential actions.

## Target architecture
ChatGPT ↔ Secure Task Bridge ↔ AURA Event Bus ↔ Brain.run() ↔ Tools/AgentController ↔ Android

## Event-first behavior
Android events should wake a lightweight AURA layer. Only meaningful events should invoke heavier AI reasoning. AURA may ignore an event, notify the user, ask for approval, or perform an authorized action.

## Planned modules
- `bridge/` — transport, pairing, device identity
- `events/` — normalized Android event bus and filtering
- `camera/` — controlled image capture
- `screen/` — controlled screen/frame capture
- `policy/` — bridge privacy and remote-action policy

These modules should be added incrementally; do not move core agent code until tests prove equivalence.

## Current status
Foundation source copied from the verified Etap 1 tree. AURA display branding is enabled. The next milestone is a secure Task Bridge that invokes the existing Brain entry point without bypassing safety controls.
