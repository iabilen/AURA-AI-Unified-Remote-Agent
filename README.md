# AURA — AI Unified Remote Agent

> **ChatGPT'nin Android üzerindeki fiziksel ajanı.**

AURA (AI Unified Remote Agent) is the second-stage evolution of the verified Ultra Agent Android foundation. The working on-device agent remains intact while AURA adds the architecture needed for a secure, event-driven connection between ChatGPT and an Android device.

## Etap 2 hedefi

```text
                 ChatGPT
                    ↕
             Secure Task Bridge
                    ↕
              AURA Event Bus
                    ↕
        Brain → Tools → AgentController
                    ↕
                 Android
```

AURA is designed to be **device-independent**. The Galaxy A54 is the first physical body, not a permanent product limitation. A future phone, tablet, PC or other authorized device can become another AURA endpoint.

## Foundation

This repository was initialized from the last verified Etap 1 build:

- Source: `iabilen/Ultra-Agent-A54chatgpt-source`
- Baseline commit: `fec145251a37d51700429104f4f4d5c1d1101e6d`
- Android: Kotlin + Jetpack Compose
- Local model: Gemma 3 1B via llama.cpp
- Device control: Android AccessibilityService + existing AgentController
- Safety: existing policy gate and confirmation flow are preserved
- License: AGPL-3.0

The original package and internal class names are deliberately preserved during the foundation phase so that the proven Android behavior is not destabilized. The user-facing application name is now **AURA**.

## AURA capabilities roadmap

### Phase A — Stable body
- On-device Gemma 3 1B
- Screen/UI observation
- Tap, type, scroll, back/home and app launch
- Existing safety gate
- Notification/event foundations

### Phase B — Secure Task Bridge
- Device pairing
- Device identity and revocation
- Authenticated HTTPS/WebSocket transport
- `Brain.run(task)` as the single execution entry point
- No open unauthenticated control port

### Phase C — Event-first assistant

```text
Android event
     ↓
AURA Event Bus
     ↓
filter / classify
     ↓
ChatGPT when useful
     ↓
ignore / notify / ask / act
```

Examples include selected notifications, SMS, calls, calendar events, battery/state changes and other authorized Android signals. The design intentionally avoids waking the heavy local model for every trivial event.

### Phase D — Eyes
- On-demand camera frames
- Screen/image capture
- OCR and visual understanding through the authorized ChatGPT path
- Optional periodic frames when explicitly enabled

Continuous camera streaming is not the default because of battery, bandwidth and privacy costs.

### Phase E — Hands everywhere
- Authorized app control
- Web navigation through the existing accessibility/tool layer
- Hardware/system controls exposed by the existing agent
- Future multi-device endpoints

## Security model

AURA must never become an anonymous remote-control server. Every remote device is paired and authenticated. High-impact actions remain behind the existing safety policy and confirmation layer unless an explicit, future risk policy authorizes otherwise.

Sensitive notification/message content should only leave the device under an explicit privacy policy.

## Build

The GitHub Actions workflow builds the Android debug APK with the same verified toolchain used by the Etap 1 success:

- JDK 17
- Android API 36
- NDK 29.0.14206865
- arm64-v8a
- llama.cpp built for Android
- debug APK artifact: `agent-ultra-a54-debug`

From `ultra-native/`:

```bash
./gradlew assembleDebug
```

## Project rule

**Do not rewrite the working agent unnecessarily.** AURA should grow around the proven `Brain → Tools → AgentController` chain. New bridge/event/camera components must be thin, testable layers that feed the existing execution core.

## License and attribution

AURA preserves the upstream project's AGPL-3.0 licensing and attribution. See `LICENSE` and `NOTICE`.

---

**AURA — AI Unified Remote Agent**

From a local Android agent to a secure physical interface for ChatGPT.
