# AURA — Repeatable Build / Handoff Record

This file is the reproducible continuation record for Etap 2.

## Baseline
`iabilen/Ultra-Agent-A54chatgpt-source` at `fec145251a37d51700429104f4f4d5c1d1101e6d` is the verified working foundation.

## Verified build
GitHub Actions run 22 succeeded before the AURA branding-only changes. Artifact: `agent-ultra-a54-debug`; SHA-256 `6877bfad9051ae2fe4668bbe9529998cbdc29e990f3bc31fa6fae62c845dd0ef`.

## Toolchain
JDK 17, API 36, NDK 29.0.14206865, arm64-v8a, llama.cpp Android build, minSdk 28.

## Important history
- CMake pin removed so runner CMake is used.
- NDK pinned to 29.0.14206865.
- App minSdk aligned to 28.
- Incompatible AppFunctions bridge was removed; custom Task Bridge remains the intended integration path.
- Compose version-catalog accessors were corrected.

## Goal
`ChatGPT → secure Task Bridge → AURA → Brain.run() → Android` with event-first wakeups, camera/screen capabilities and device portability.

## Safety
Do not expose an unauthenticated remote-control endpoint. Preserve the existing gate/confirmation behavior for consequential actions.
