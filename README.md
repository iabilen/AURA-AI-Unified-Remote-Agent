# AURA — AI Unified Remote Agent

> **ChatGPT'nin Android üzerindeki fiziksel ajanı.**

AURA (AI Unified Remote Agent) is the second-stage evolution of the verified Ultra Agent Android foundation. The working Android agent remains intact while AURA adds the architecture needed for a secure, event-driven connection between **ChatGPT as the primary AI** and an Android device.

## Core architecture

```text
                    CHATGPT 🧠
                        ↕
                 AURA Relay / MCP
                        ↕ WSS
                Secure Task Bridge
                        ↕
                 AURA Event Bus
                        ↕
          Brain → Tools → AgentController
                        ↕
                    Android 📱
```

AURA is designed to be **device-independent**. The Galaxy A54 is the first physical body, not a permanent product limitation. A future phone, tablet, PC or other authorized device can become another AURA endpoint.

## ChatGPT-first rule

**ChatGPT is the main intelligence and decision layer.** AURA is the body: it observes authorized Android events, exposes eyes/hands/hardware capabilities and executes tasks through the existing safety path.

The local Gemma/llama.cpp engine is **not the main AI**. It remains only as an optional on-device fallback/continuity component; AURA's remote decision architecture is built around ChatGPT.

## Event-first assistant

```text
Android event
     ↓
AURA Notification/Event source
     ↓
durable local queue
     ↓ WSS
AURA Relay
     ↓ MCP
ChatGPT
     ↓
IGNORE / INFORM / ASK / ACT
     ↓
AURA → Brain.run() → Android
```

Notifications are the first wake source. The same event pipeline is intended for SMS, calls, calendar, battery/state changes and other authorized Android signals.

## Current relay

`relay/` is the first real external relay/MCP implementation:

- authenticated `/device` WebSocket endpoint for AURA phones;
- authenticated `/mcp` remote MCP endpoint;
- stable task IDs and device result correlation;
- bounded recent event buffer;
- `aura_devices`, `aura_status`, `aura_events`, `aura_send_task` tools;
- independent GitHub Actions typecheck.

The phone is outbound-only: it never opens a public remote-control socket.

## Safety and privacy

Every remote task enters the existing AURA/Ultra Agent execution path. High-impact actions remain subject to its confirmation/safety behavior. Notification content is not sent anywhere unless the bridge is configured; when configured, the relay transport is authenticated.

A remote MCP server does not automatically force every ChatGPT client surface to wake on arbitrary external events. The relay therefore makes events durable and available to the supported MCP integration; true unsolicited host-side wake/subscription behavior depends on the capabilities of the target ChatGPT surface.

## Foundation

This repository was initialized from the last verified Etap 1 build:

- Source: `iabilen/Ultra-Agent-A54chatgpt-source`
- Baseline commit: `fec145251a37d51700429104f4f4d5c1d1101e6d`
- Android: Kotlin + Jetpack Compose
- Device control: Android AccessibilityService + existing AgentController
- Safety: existing policy gate and confirmation flow are preserved
- Optional local model: Gemma 3 1B via llama.cpp
- License: AGPL-3.0

The original package and internal class names are deliberately preserved during the foundation phase so that proven Android behavior is not destabilized.

## Next stages

1. Device pairing and revoke instead of bootstrap bearer tokens.
2. Persistent foreground bridge lifecycle and battery-aware wake behavior.
3. More event adapters: SMS, calls, calendar, battery/connectivity and boot.
4. Camera/screen tools and on-demand visual input to ChatGPT.
5. Event filtering and privacy policies before forwarding sensitive content.
6. Durable relay-side storage and multi-device routing.
7. Verified ChatGPT MCP/event subscription path for genuine proactive wake behavior.

## Build

The Android GitHub Actions workflow uses the verified toolchain:

- JDK 17
- Android API 36
- NDK 29.0.14206865
- arm64-v8a
- minSdk 28

From `ultra-native/`:

```bash
./gradlew assembleDebug
```

Relay typecheck:

```bash
cd relay
npm install
npm run typecheck
```

## Project rule

**Do not rewrite the working agent unnecessarily.** AURA grows around the proven `Brain → Tools → AgentController` chain. Bridge, relay, event, camera and multi-device components stay thin and testable.

## License and attribution

AURA preserves the upstream project's AGPL-3.0 licensing and attribution. See `LICENSE` and `NOTICE`.

---

**AURA — AI Unified Remote Agent**

ChatGPT's intelligence, AURA's Android body.
