# Single Brain + OpenAI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove AURA’s Gemma/local-LLM dependency while preserving the original Brain and connect that Brain to OpenAI/ChatGPT as the single external AI intelligence path.

**Architecture:** Keep `AuraRuntime → Brain → Tools → ActionGate → Android → Verification → Event Queue/ACK`. Brain remains the orchestration center. The existing `OpenAiClient` becomes the provider boundary; local llama.cpp/model execution is removed only after dependency audit proves it is no longer needed.

**Tech Stack:** Kotlin, Android, OkHttp, existing Brain/Tools/Gate/Verification/Event Queue, OpenAI API.

**Spec:** `docs/AURA_STATUS.md` — section `2026-10-05 decision: single Brain + OpenAI/ChatGPT`.

## Global Constraints

- Preserve the existing Brain behavior and keep it as the central orchestrator.
- Do not add IntentRouter, ActionContract, GemmaAdapter, FunctionGemma, or another agent layer.
- Remove Gemma, FunctionGemma and local llama.cpp inference from the normal AURA runtime path.
- OpenAI/ChatGPT is the single external AI intelligence path.
- Tools, ActionGate, Android execution and Verification remain authoritative.
- Preserve Event Queue/ACK semantics.
- AURA runtime memory remains device-local and must not depend on ChatGPT.
- API credentials must never be committed to GitHub or packaged into the APK.
- Network/provider failure must fail safely; no action may be reported successful without verification.
- No APK build or replacement unless explicitly requested by the user.
- Do not merge into `main`.

## Review Focus

- Provider unavailable/offline: Brain returns an explicit safe failure and never falls back to a removed local model.
- OpenAI response malformed/empty: Brain does not execute an unparsed or unverified action.
- Timeout/cancellation: in-flight provider work is cancelled/failed without leaving a pending execution state.
- Gate denial/verification failure: provider output cannot bypass existing authorization or fabricate success.
- Credential handling/logging: API keys never appear in logs, tests, source, or packaged resources.

---

### Task 1: Inventory and dependency lock

**Files:**
- Read: `ultra-native/app/src/main/java/com/agent/ultra/agent/Brain.kt`
- Read: `ultra-native/app/src/main/java/com/agent/ultra/aura/AuraRuntime.kt`
- Read: `ultra-native/app/src/main/java/com/agent/ultra/provider/OpenAiClient.kt`
- Read: `ultra-native/app/src/main/java/com/agent/ultra/provider/ProviderConfig.kt`
- Read: `ultra-native/app/src/main/java/com/agent/ultra/local/LocalModelEngine.kt`
- Read: `ultra-native/app/src/main/java/com/agent/ultra/local/LlmNative.kt`
- Read: `ultra-native/app/src/main/cpp/ultra_llm.cpp`
- Read: `ultra-native/app/src/main/cpp/CMakeLists.txt`
- Read: `ultra-native/app/build.gradle.kts`
- Read: `.github/workflows/build-a54-apk.yml`

**Interfaces:**
- Consumes: current branch at `aura/pre-device-hardening-plan`, commit `e3ed406`.
- Produces: a verified list of every local-model consumer and every model-only build/runtime dependency.

- [ ] **Step 1: Search all Android sources/resources/build files for `LocalModelEngine`, `LlmNative`, `ultra_llm`, `llama`, `Gemma`, model presets, model download UI and local-model settings.**
- [ ] **Step 2: Trace every reference to `Brain(...)` and every local-model method call.**
- [ ] **Step 3: Classify each reference as removable, provider-facing, or unrelated.**
- [ ] **Step 4: Record the dependency map in the implementation commit message/notes; do not change code yet.**

### Task 2: Make Brain single-provider without redesign

**Files:**
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/agent/Brain.kt`
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/aura/AuraRuntime.kt`

**Interfaces:**
- Consumes: existing `OpenAiClient` and `ProviderConfig`.
- Produces: `Brain(Context)` with the same public `run`, answer listener, gate and tool behavior; `AuraRuntime` constructs Brain without `LocalModelEngine`.

- [ ] **Step 1: Add/update a focused test that Brain construction has no local-model dependency.**
- [ ] **Step 2: Remove the `LocalModelEngine` constructor parameter from Brain and remove its initialization/import.**
- [ ] **Step 3: Remove the offline/local fallback path, `isSimpleLocalIntent`, `emitLocal`, `runLocalLoop`, and local escalation logic.**
- [ ] **Step 4: Change `configured`/`modelLabel` semantics to describe the configured OpenAI provider only; never advertise Gemma/on-device fallback.**
- [ ] **Step 5: Keep the existing cloud tool loop, memory, recipes, scam detection, Gate, verification and answer/bridge callbacks unchanged except where compilation requires the local dependency removal.**
- [ ] **Step 6: Change `AuraRuntime` to instantiate `Brain(context)` directly.**
- [ ] **Step 7: Run the focused Brain/provider unit tests and fix only failures caused by this change.**

### Task 3: Make OpenAI the explicit provider boundary

**Files:**
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/provider/OpenAiClient.kt`
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/provider/ProviderConfig.kt`
- Modify: `ultra-native/app/src/test/java/com/agent/ultra/...` (focused provider tests; exact existing test location chosen after inventory)

**Interfaces:**
- Consumes: Brain’s existing `List<ChatMessage> → Result<String>` contract.
- Produces: an OpenAI-backed implementation with explicit timeout/error semantics and no secret leakage.

- [ ] **Step 1: Write failing tests for success, non-2xx, empty output, timeout/cancellation and malformed response.**
- [ ] **Step 2: Verify the current OpenAI API contract from official OpenAI documentation before changing request format.**
- [ ] **Step 3: Prefer the current Responses API for a new direct OpenAI integration while preserving Brain’s existing text/result contract; do not redesign Brain around provider-specific response objects.**
- [ ] **Step 4: Use a configurable model ID and OpenAI API base URL; do not hard-code a user credential or embed it in source/resources.**
- [ ] **Step 5: Ensure request/response logging never includes the API key or full sensitive payloads.**
- [ ] **Step 6: Preserve streaming only if it is actually used by the existing UI path; otherwise keep the smallest working provider surface.**
- [ ] **Step 7: Run provider tests and verify failure messages are safe and actionable.**

### Task 4: Remove local model/native inference infrastructure

**Files:**
- Delete after Task 1 proves no remaining consumers: `ultra-native/app/src/main/java/com/agent/ultra/local/LocalModelEngine.kt`
- Delete after Task 1 proves no remaining consumers: `ultra-native/app/src/main/java/com/agent/ultra/local/LlmNative.kt`
- Delete after Task 1 proves no remaining consumers: `ultra-native/app/src/main/cpp/ultra_llm.cpp`
- Delete or simplify after dependency audit: `ultra-native/app/src/main/cpp/CMakeLists.txt`
- Modify: `ultra-native/app/build.gradle.kts`
- Modify if needed: `ultra-native/build.gradle.kts`, `ultra-native/gradle/libs.versions.toml`
- Modify: `.github/workflows/build-a54-apk.yml` so CI no longer prepares llama.cpp/model assets
- Modify model-related UI/settings/assets only where inventory proves they are exclusively local-model features

**Interfaces:**
- Consumes: completed dependency map from Task 1.
- Produces: Android build without llama.cpp/local model code or model-only assets.

- [ ] **Step 1: Write a regression test/static check proving no production source references removed local-model symbols.**
- [ ] **Step 2: Remove model/native source and build wiring in the smallest changeset.**
- [ ] **Step 3: Remove only model-specific CI setup; do not alter unrelated CI gates.**
- [ ] **Step 4: Remove stale model-download/settings UI only if it has no remaining provider purpose.**
- [ ] **Step 5: Re-run dependency/static checks before compiling.**

### Task 5: Safety and provider failure tests

**Files:**
- Modify/Create focused tests under `ultra-native/app/src/test/java/com/agent/ultra/` as required.

**Interfaces:**
- Consumes: single-provider Brain from Tasks 2–4.
- Produces: regression coverage for the safety boundary.

- [ ] **Step 1: Test provider unavailable/offline → explicit failure, no local fallback, no tool execution.**
- [ ] **Step 2: Test malformed/ambiguous model output → no unsafe execution.**
- [ ] **Step 3: Test Gate denial remains authoritative.**
- [ ] **Step 4: Test verification failure cannot become a success response.**
- [ ] **Step 5: Test Event Queue/ACK behavior remains unchanged for Brain-generated results.**
- [ ] **Step 6: Test API credentials are not present in logs or serialized error messages.**

### Task 6: Verification and audit

**Files:**
- No planned production changes unless verification exposes a real defect.
- Read: changed files and CI configuration.

- [ ] **Step 1: Run the focused unit suite covering changed Brain/provider/Gate behavior.**
- [ ] **Step 2: Run static/reference scan for `Gemma`, `FunctionGemma`, `LocalModelEngine`, `LlmNative`, `ultra_llm`, `llama.cpp` and model-download remnants.**
- [ ] **Step 3: Verify Event Queue/ACK, Gate, Tools, Verification and Android service paths were not structurally redesigned.**
- [ ] **Step 4: Run the repository’s existing focused CI-equivalent checks; document unrelated baseline failures rather than broadening scope.**
- [ ] **Step 5: Review the final diff for secrets, accidental APK artifacts, unrelated refactors and `main` changes.**
- [ ] **Step 6: Commit the implementation on `aura/pre-device-hardening-plan`; do not merge to `main`.**
- [ ] **Step 7: Stop before APK generation. APK requires a separate explicit user request.**

## OpenAI API boundary

The current repository already contains an `OpenAiClient` and a provider configuration abstraction. The implementation should reuse that boundary rather than introducing a new AI framework. OpenAI’s current documentation says the Responses API is the recommended API for new integrations; the implementation should therefore evaluate a minimal Responses API transport while keeping the Brain-facing contract stable. The OpenAI API is separately billed from ChatGPT subscriptions.

## Explicit non-goals

- No second AI model.
- No local Gemma fallback.
- No FunctionGemma/Mobile Actions.
- No new orchestration framework.
- No Brain rewrite.
- No Event Queue/ACK redesign.
- No new memory backend.
- No APK build.
- No merge to `main`.
