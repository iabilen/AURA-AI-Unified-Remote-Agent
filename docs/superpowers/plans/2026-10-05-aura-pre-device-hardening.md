# AURA Pre-Device Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the AURA relay and Android bridge production-testable on Android 15+ before any physical A54 validation by fixing deterministic relay shutdown, migrating the long-running bridge away from `dataSync`, and preventing notification payloads from persistent logs.

**Architecture:** Keep AURA runtime memory entirely local/offline. The relay remains an authenticated transport and operational-state layer. The Android bridge remains the event/ACK transport path, but its persistent service lifecycle uses a valid Android 15-compatible foreground-service type (`connectedDevice`) for the network/device bridge rather than `dataSync`; notification title/body may remain transient runtime input but must never enter persistent operational logs.

**Tech Stack:** TypeScript/Node 20/tsx/ws/Room/PostgreSQL relay integration; Kotlin/Java Android app targeting SDK 35 with compile SDK 36; JUnit/Android instrumentation; GitHub Actions.

**Spec:** AURA Pre-Device Hardening Specification approved in conversation on 2026-10-05.

## Global Constraints

- **No physical A54 testing until every code, test, CI, audit, and fresh-APK gate passes.**
- **No merge to `main`.**
- **AURA runtime memory is local/offline only; GitHub, Hjarni, Cortex, Dropbox, and other external stores are engineering/backup surfaces only.**
- **Preserve the existing event queue/ACK/in-flight semantics unless tests prove a necessary change.**
- **Do not solve the Android 15 `dataSync` problem by merely adding `onTimeout()`; migrate the service architecture to a valid type.**
- **Notification title/text/body must not be persisted in `notifications.log` or scam operational logs.**
- **Use TDD: failing regression test first, verify failure, minimal fix, focused verification, then full verification.**
- **Fresh APK is produced only after code/tests/CI/audit are green.**

## Review Focus

- Relay receives SIGTERM/SIGINT while HTTP/WebSocket handles are still open → process must exit deterministically and close both server and database.
- Relay shutdown is triggered more than once or while startup/shutdown is partially complete → shutdown must be idempotent and must not double-close or hang.
- Android service is started from boot/package replacement on target SDK 35 → manifest/service type must not be `dataSync`, and the lifecycle must use the Android 15-compatible bridge architecture.
- Notification capture contains arbitrary title/text or scam-trigger data → persistent logs must contain operational metadata only, never notification payload content.
- Existing queue/ACK behavior and authenticated reconnect paths regress while service/lifecycle code changes → existing Android queue tests and relay smoke/auth/persistence tests must remain green.

---

### Task 1: Make Relay Shutdown Deterministic

**Files:**
- Modify: `relay/src/index.ts`
- Modify: `relay/smoke-test.mjs`
- Create: `relay/shutdown-test.mjs`
- Modify: `relay/package.json`

**Interfaces:**
- `relay/src/index.ts` must expose no new public API; internal shutdown must own HTTP server, WebSocket server, database, and pending-task cleanup.
- `relay/shutdown-test.mjs` must launch the relay with isolated temp storage and assert both SIGTERM and SIGINT terminate the child promptly.
- `relay/package.json` adds a dedicated `test:shutdown` script and CI invokes it.

- [ ] **Step 1: Write the failing SIGTERM regression test**
  - Add a child-process test that starts `src/index.ts`, waits for `/healthz`, sends SIGTERM, and fails if the child has not emitted `exit` within a bounded timeout.
  - Assert the child exits with a normal signal-derived status rather than remaining alive because of an open HTTP/WebSocket handle.

- [ ] **Step 2: Run the regression test and verify it fails**
  - Run: `cd relay && npm run test:shutdown`
  - Expected: FAIL/hang against the current implementation because only the database is closed while the HTTP server remains open.

- [ ] **Step 3: Write the SIGINT half of the failing regression coverage**
  - Extend `relay/shutdown-test.mjs` so the same deterministic-exit assertion is exercised with SIGINT as well as SIGTERM.
  - Keep each child isolated so one shutdown path cannot mask the other.

- [ ] **Step 4: Implement one idempotent shutdown path in `relay/src/index.ts`**
  - Introduce an internal `shutdown(signal: string): Promise<void>` guard that can run only once.
  - Close the HTTP server and WebSocket server, resolve/clear pending task timers, close the relay database, then allow process exit.
  - Register both SIGTERM and SIGINT against this same function.
  - Do not change event persistence, auth, routing, or MCP behavior.

- [ ] **Step 5: Run focused relay verification**
  - Run: `cd relay && npm run test:shutdown`
  - Expected: PASS for both signals with bounded completion.
  - Run: `cd relay && npm run typecheck`
  - Expected: PASS.

- [ ] **Step 6: Harden the existing smoke test against future hangs**
  - Make `relay/smoke-test.mjs` use a bounded child-exit helper instead of waiting forever on `exit`.
  - Keep its existing event ACK, persistence-across-restart, and MCP auth assertions unchanged.

- [ ] **Step 7: Wire shutdown regression into relay CI**
  - Add `npm run test:shutdown` to `.github/workflows/build-aura-relay.yml` after typecheck/registry/database tests and before the full smoke test.
  - Verify the workflow still runs the existing stages.

- [ ] **Step 8: Run the complete relay suite**
  - Run: `cd relay && npm install && npm run typecheck && npm run test:registry && npm run test:database && npm run test:shutdown && node smoke-test.mjs`
  - Expected: all commands PASS and the smoke test terminates without manual intervention.

- [ ] **Step 9: Commit**
  - Commit message: `fix: make relay shutdown deterministic`

### Task 2: Migrate Android Bridge FGS Away From `dataSync`

**Files:**
- Modify: `ultra-native/app/src/main/AndroidManifest.xml`
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/AgentBackgroundService.java`
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/AuraBootReceiver.java`
- Create: `ultra-native/app/src/androidTest/java/com/agent/ultra/aura/AgentBackgroundServiceManifestTest.kt`
- Modify: `ultra-native/app/build.gradle.kts` only if required by the selected test/build configuration.

**Interfaces:**
- `AgentBackgroundService` continues to return `START_STICKY`, preserve authenticated bridge/runtime startup, and preserve existing event-trigger registration.
- Manifest declares `android:foregroundServiceType="connectedDevice"` and `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`; remove the obsolete `FOREGROUND_SERVICE_DATA_SYNC` declaration if no other service uses it.
- `AgentBackgroundService` calls `startForeground()` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on API levels that support typed foreground services, while retaining the existing compatibility path for older API levels.
- `AuraBootReceiver` continues handling BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, and MY_PACKAGE_REPLACED without relying on a forbidden `dataSync` boot launch.

- [ ] **Step 1: Add a failing manifest/lifecycle regression test**
  - Add an Android instrumentation test that resolves `AgentBackgroundService` through `PackageManager` and asserts its declared foreground-service type contains `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` and does not contain `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
  - Assert the package declares `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`.

- [ ] **Step 2: Run the focused Android test and verify it fails**
  - Run the project's connected Android test task for the new class using the existing Gradle configuration.
  - Expected: FAIL because the current manifest declares `dataSync` and only declares `FOREGROUND_SERVICE_DATA_SYNC`.

- [ ] **Step 3: Apply the minimal manifest migration**
  - Replace `dataSync` with `connectedDevice` on `AgentBackgroundService`.
  - Add `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
  - Retain `CHANGE_NETWORK_STATE`/`CHANGE_WIFI_STATE` and existing Bluetooth permissions because Android's `connectedDevice` type accepts these as runtime prerequisites.
  - Do not introduce a different FGS type or an unrelated service redesign.

- [ ] **Step 4: Update `startForeground()` to pass the matching type**
  - Use the Android typed `startForeground` overload/constant appropriate to API 29+ and `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
  - Keep the existing notification and AURA runtime startup behavior intact.
  - Preserve `START_STICKY`, `onTaskRemoved`, and the authenticated WebSocket recovery path.

- [ ] **Step 5: Reconcile boot/package-replacement startup**
  - Verify the receiver still uses the standard foreground-service start path for O+.
  - Ensure no code path explicitly requests the `dataSync` type from boot/package replacement.
  - Keep the existing `IllegalStateException` handling so Android can defer a restart if another OS restriction applies.

- [ ] **Step 6: Run focused Android verification**
  - Run the new manifest test and the existing `AuraEventQueueTest` suite.
  - Expected: PASS, with queue persistence, ACK, in-flight protection, ordering, and transport-coalescing behavior unchanged.

- [ ] **Step 7: Run the full Android build/test gate**
  - Run the repository's configured debug build plus available unit/instrumentation checks without a physical device.
  - Expected: build succeeds for target SDK 35 and no manifest/foreground-service lint or merge error remains.

- [ ] **Step 8: Commit**
  - Commit message: `fix: migrate aura bridge to connected device foreground service`

### Task 3: Remove Notification Payloads From Persistent Logs

**Files:**
- Modify: `ultra-native/app/src/main/java/com/agent/ultra/UltraNotificationService.kt`
- Create: `ultra-native/app/src/test/java/com/agent/ultra/NotificationLogSanitizationTest.kt`
- Modify or create a small logging-sanitizer helper only if needed to make the policy independently unit-testable.

**Interfaces:**
- Runtime `publishAuraEvent()` continues receiving bounded notification title/text because transient runtime behavior is explicitly allowed.
- Persistent log formatting accepts operational metadata such as timestamp, package, and a boolean scam-warning indicator, but never accepts raw notification title/text or arbitrary `EventTrigger.Action.data` as log content.

- [ ] **Step 1: Extract/test the persistent-log formatting boundary**
  - Add a small pure formatter/sanitizer with a stable testable signature, or equivalent private testable boundary, that accepts package/scam metadata and returns only safe operational log text.
  - Do not make the runtime event payload itself use the sanitized representation.

- [ ] **Step 2: Write failing privacy tests**
  - Test that a notification title containing a unique secret marker is absent from the persistent log line.
  - Test that notification body text containing a unique secret marker is absent.
  - Test that scam-trigger `Action.data` containing a unique marker is absent from operational logs.
  - Test that safe metadata such as package name, timestamp, and a generic warning marker remains available.

- [ ] **Step 3: Run privacy tests and verify they fail**
  - Run the focused JVM test class.
  - Expected: FAIL because the current implementation writes truncated title/text and scam data into logs.

- [ ] **Step 4: Implement minimal sanitization**
  - Remove title/text appends from `notifications.log`.
  - Replace `SCAM WARN from=${pkg}: ${a.data}` with a payload-free operational message that records only the package and generic warning state.
  - Preserve `publishAuraEvent()` and user-facing scam warning behavior unchanged.

- [ ] **Step 5: Run focused privacy verification**
  - Run the new JVM tests.
  - Expected: PASS with unique secret markers absent from all persistent-log output.

- [ ] **Step 6: Run Android regression suite**
  - Run existing Android queue/event tests and app build checks.
  - Expected: PASS; notification runtime event publication and queue semantics remain intact.

- [ ] **Step 7: Commit**
  - Commit message: `fix: keep notification payloads out of persistent logs`

### Task 4: End-to-End Audit, CI, Fresh APK, and Device Gate

**Files:**
- Inspect: all files changed by Tasks 1–3 plus CI workflows.
- Modify: only if the audit finds a regression directly caused by Tasks 1–3.

**Interfaces:**
- No new runtime architecture is introduced in this task.
- The release gate consumes the exact test/build artifacts from the previous tasks.

- [ ] **Step 1: Re-audit the three changed boundaries**
  - Relay shutdown: confirm server, WebSocket, DB, timers, and signal handlers share one idempotent lifecycle.
  - Android FGS: confirm no `dataSync` declaration or boot dependency remains for the AURA bridge.
  - Privacy: search the source for notification title/text and scam action data reaching persistent file/log APIs.

- [ ] **Step 2: Run all local gates from a clean dependency state**
  - Relay: install dependencies, typecheck, registry tests, database tests, shutdown test, smoke test.
  - Android: debug build plus available unit/instrumentation tests, including `AuraEventQueueTest` and the new manifest/privacy tests.
  - Expected: zero failures and no hanging process.

- [ ] **Step 3: Push the isolated branch and verify fresh GitHub Actions**
  - Confirm CI runs against the new commits.
  - Wait for every required workflow/job to complete.
  - Do not treat an earlier green run from an older commit as evidence for the new code.

- [ ] **Step 4: Produce a fresh debug APK**
  - Build the APK from the hardened commit only after all preceding gates are green.
  - Record the exact commit SHA, APK artifact/run, and build result.
  - Do not reuse the older APK built before these fixes.

- [ ] **Step 5: Final pre-device audit**
  - Confirm the branch is not merged to `main`.
  - Confirm AURA runtime has no GitHub/Hjarni/Cortex/Dropbox dependency.
  - Confirm the APK corresponds to the fully verified commit.
  - Confirm no known blocker remains.

- [ ] **Step 6: Stop before physical A54 validation**
  - Only after all gates above are green, report that the codebase is ready for the A54 test phase.
  - Do not install or exercise the APK on the physical A54 as part of this plan.

---

## Expected Final Gate

The implementation is considered ready for physical-device validation only when:

1. Relay typecheck, registry, database, shutdown, and smoke tests pass without hanging.
2. SIGTERM and SIGINT close relay server/WebSocket/DB and terminate promptly.
3. Android target SDK 35 build passes with `connectedDevice` FGS declaration and matching permission/type.
4. Boot/package replacement no longer relies on `dataSync` FGS startup.
5. Notification title/body and scam payload markers cannot reach persistent operational logs.
6. Existing Android event queue/ACK/in-flight tests remain green.
7. Fresh GitHub Actions runs are green for the hardened commit.
8. A fresh APK is produced from that exact verified commit.
9. No physical A54 testing has occurred before these gates.
