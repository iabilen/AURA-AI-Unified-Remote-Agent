# Agent Ultra / AURA

**An Android agent whose original Ultra Brain remains the central orchestrator.**

The current direction deliberately keeps the project small: one Brain, the existing
tool/action safety stack, and one external AI provider. Gemma, FunctionGemma and the
previous on-device llama.cpp model runtime are no longer part of the AURA runtime.

## Current architecture

```text
User / Device Event
        ↓
      Brain
        ↓
      Tools
        ↓
   ActionGate
        ↓
     Android
        ↓
   Verification
        ↓
 Event Queue / ACK
```

The Brain keeps the existing orchestration, memory, recipes, scam checks, tool loop,
gate and verification behavior. We are not introducing a second agent framework or
rewriting Brain into multiple new layers.

## AI provider

AURA uses OpenAI through the existing provider boundary. The Android client sends the
bounded conversation context needed for the current turn to the OpenAI Responses API
and uses `store=false`, keeping AURA runtime continuity device-local. The model ID is
configurable; the current default is `gpt-6-luna`.

The API key is supplied by the user at runtime and is not stored in source control or
bundled into the APK. A provider failure does not fall back to another AI model and
does not bypass the local ActionGate or verification layer.

## What AURA still does

- Drives allowed Android tools through the existing accessibility/device layer.
- Maintains the existing deterministic safety gate.
- Verifies actions before reporting success.
- Preserves Event Queue / ACK semantics.
- Stores runtime continuity locally on the device.
- Supports the existing chat and voice surfaces.
- Remembers successful task sequences and routines through the existing memory system.

## Current limits

- A live OpenAI API call has not yet been exercised with a real user credential.
- Full A54 physical end-to-end validation is still pending.
- APK generation remains a separate explicit step and is not part of normal verification CI.

## Development rule

Do not expand the architecture unless the existing Brain/Ultra design proves insufficient.
Do not build or replace an APK unless explicitly requested. Do not merge hardening work
into `main` before the agreed gates pass.
