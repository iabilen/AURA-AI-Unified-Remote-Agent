# AURA V2 Technical Roadmap

Status: planning scaffold created; implementation has not started.

## Safety and version boundaries

- `main` remains the untouched V1 baseline.
- `v1-archive-2026-10-31` pins the V1 baseline commit `dad5e8137636dc56c6ceb85ad99376465de5bcc1`.
- All V2 work takes place on `develop/aura-v2`.
- Do not merge V2 into `main`, publish an APK, or release externally without İhsan's explicit approval.
- Prefer additive modules. Before modifying an existing file, explain the reason and obtain approval.

## Phases

1. Read-only code audit: map modules, Android/native boundaries, dependencies, current branch state, CI and tests; distinguish working code from placeholders.
2. Core contracts: validate or design AuraRuntime, EventBus, durable EventQueue with ACK/retry, ContinuityMemory, ActionGate, DeviceBridge and Verification.
3. ChatGPT bridge: authenticated bidirectional relay; define IGNORE / INFORM / ASK / ACT decision contract and reconnect behavior.
4. Android event adapters: battery/charging/thermal, connectivity, notifications, calendar, call/SMS metadata, screen/lock and boot events, each with explicit permission handling.
5. Android action adapters: app launch/navigation, user-approved communication, notification/settings actions and permission-bounded UI automation.
6. Product UI: device/connection state, event history, decision explanations, approval queue, permission controls and local-memory settings.
7. Device validation: Samsung Galaxy A54 first; screen-off operation, process/boot recovery, network loss, duplicate events/actions, battery/memory use and permission revocation.

## First end-to-end milestone

Battery reaches 15% → Android adapter emits an event → durable queue stores it → ChatGPT evaluates it → AURA displays the resulting notification → event and outcome are recorded. Do not broaden the feature set until this flow is tested end-to-end.

## Immediate next action

Complete the read-only V1 repository audit before implementing modules. Record actual code locations, build/test status and reusable components. No source implementation is changed by this scaffold commit.
