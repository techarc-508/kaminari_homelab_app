# Phase 3 Remediation Report

**Date:** 2026-11-09
**Input:** `PHASE_3_AUDIT_REPORT.md` (independent verdict: FAIL)
**Result:** all Critical/High code findings fixed; Medium/Low resolved or explicitly accepted.
**Verdict after remediation:** PASS WITH CONDITIONS — device matrix still outstanding.
**Scope:** Phase 3 only. No Phase 4 work was started.

---

## 1. Summary

The audit found one fatal platform path, three High behavioural defects, and a false Phase 3 report
claim. Remediation introduced a shared delivery-status source, real Android 15 timeout handling,
terminal-error observation, Room-backed deep links, testable boot/timeout components, two-mode
delivery with legacy migration, and real regression tests. The original audit is preserved
unmodified with appended evidence in §11 of `PHASE_3_AUDIT_REPORT.md`.

Final automated evidence (run separately, `--rerun-tasks`):

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | exit 0 — 173 tests, 0 failures, 0 errors, 2 pre-existing skips |
| `./gradlew assembleDebug --rerun-tasks` | exit 0 — `app-debug.apk`, 19,557,690 bytes |
| `./gradlew lint --rerun-tasks` | exit 0 — 0 errors, 56 warnings |

No device/emulator was available; nothing here is a hardware validation.

---

## 2. Finding-by-finding remediation

### A-01 (Critical) — Android 15 `dataSync` timeout crash

**Root cause:** the service declared `foregroundServiceType="dataSync"` and targeted SDK 36, but did
not override `Service.onTimeout(int,int)`. Android 15 calls it after 6 h of `dataSync` foreground
time in 24 h; without a prompt `stopSelf()` the process gets a fatal `RemoteServiceException`.

**Fix:**
- `InstantForegroundService.onTimeout(startId, fgsType)` (API 35+) publishes `TIMED_OUT` with an
  explanation, posts a non-ongoing notice, removes the foreground notification, cancels the engine,
  closes the repository, persists `BATTERY_SAVER`, schedules catch-up via `InstantTimeoutFallback`,
  and calls `stopSelf(startId)`.
- The fallback runs on the app-owned scope because `onDestroy` cancels the service scope.
- Documentation states the budget is a platform restriction and is not extended.

**Tests:** `InstantForegroundServiceTest` (timeout → `TIMED_OUT`, terminal, stopped),
`InstantTimeoutFallbackTest` (mode persisted + work scheduled), `DeliveryStatusTest` (terminal
mapping).

### A-02 (High) — dead stream advertised as live

**Root cause:** `startSubscription()` returns immediately; the service then suspended in
`awaitCancellation()` without observing `connectionState`, `lastError`, or terminal state. Terminal
errors are values in the repository, not exceptions.

**Fix:**
- Added `NftyRepository.isTerminal` (`StateFlow<Boolean>`), set on terminal auth/forbidden errors,
  empty topics, and unproductive-retry exhaustion; reset by each new subscription.
- The service combines `connectionState`, `isTerminal`, and `lastError`, mirrors phases into the
  status store, and refreshes the ongoing notification text.
- On terminal: publish the real phase/reason, post a stopped notice, `stopSelf()`.
- Cancellation still rethrows; `onDestroy` closes the repository and scope.

**Tests:** `NftyRepositoryTest` asserts terminal auth sets `isTerminal` and retriable errors do not;
`NftyNotifierTest` asserts reconnecting/timed-out text never says "live"; status mapping tests.

**Residual limitation:** the terminal branch in the service is verified by inspection and by its
dependencies' tests, not by a full service integration test (would require a configured profile,
Keystore credential, and a mock server inside a Robolectric service). This is stated in the final
audit.

### A-03 (High) — Instant mode reported DISCONNECTED

**Root cause:** the ViewModel stops its own `StreamRepository` in Instant mode while the service uses
a separate `NftyRepository`; the UI derived `authState` only from the stopped ViewModel repository.

**Fix:**
- Added `DeliveryStatus`, `DeliveryEngine`, `DeliveryPhase`, and process-wide `DeliveryStatusStore`
  in `AppContainer`.
- The service publishes every phase and the observed auth result.
- The ViewModel observes the store; in Instant mode it derives `authState` from the service status
  and ignores its own stopped stream. It never opens a second subscription.
- Settings shows the real Instant status line, including terminal reasons.

**Tests:** `DeliveryStatusTest` mapping; `MainViewModelTest` "instant mode reflects live service
state without opening a second stream" and terminal-reason banner; `StreamRepository.startCalls`
remains empty.

### A-04 (High) — cold-start deep link failed

**Root cause:** `MainActivity` consumed the pending ID on first composition, while `UiState.messages`
was empty; `openMessageFromNotification` only searched that in-memory window.

**Fix:**
- `openMessageFromNotification` runs in `viewModelScope`, resolves the profile from memory or
  DataStore, checks the loaded window, then calls `MessageRepository.getById` (new DAO query).
- Missing/deleted messages show "Notification is no longer in local history"; no empty detail.
- Warm and cold paths share the same logic.

**Tests:** `MainViewModelTest` cold-start case with an empty flow and a persisted row + genuinely
missing target; `MessageDaoTest` lookup outside the recent window and profile isolation.

### A-05 (Medium) — foreground-start failure invisible

**Fix:** `onStartCommand` publishes `FAILED` with a readable reason before stopping; the ViewModel
surfaces terminal status as a banner. The notice is posted when notifications are permitted.
**Tests:** terminal status banner in `MainViewModelTest`; status mapping.

### A-06 (Medium) — vacuous clock test

**Fix:** `NiftyTopBarClockTest` now renders the real `NftyTopBar` with an injected clock, asserts the
initial time, asserts no change within a second, advances to the next second and asserts the update,
and asserts the composition reaches idle (the old frame loop threw `AppNotIdleException`).
Production fix (`delay` to the second boundary) is preserved.

### A-07 (Medium) — delivery engines untested

**Added tests:**
- `InstantForegroundServiceTest` — timeout stop path.
- `InstantTimeoutFallbackTest` — fallback persistence + scheduling.
- `BootDeliveryRestorerTest` — both boot modes; Instant never starts a service at boot.
- `BatterySaverWorkerTest` — mode guard and no-profile success (real WorkManager worker instance).
- `NftyRepositoryTest` — terminal flag.
- `MessageDaoTest` — deep-link lookup.
- `NftyNotifierTest` — state-aware text and stopped notice.

These are JVM/Robolectric tests. They do not replace the device matrix.

### A-08 (Medium) — third "Saved only" mode

**Fix:** removed `SAVED` from the selectable enum. `DeliveryMode.fromPersisted` maps legacy
`SAVED` → `BATTERY_SAVER`; missing/unknown → `BATTERY_SAVER`; only `INSTANT` and `BATTERY_SAVER`
are exposed. Fresh installs default to Battery Saver. Saved *messages* remain untouched.
**Tests:** `DeliveryModeTest`; default-mode assertion in `MainViewModelTest`.

**Safety check:** no persisted user data is deleted. The only semantic change is that a
foreground-only selection now receives inexact background catch-up, which is the owner-requested
migration and strictly increases delivery (subject to WorkManager).

### A-09 (Medium) — boot scope and coverage

**Fix:** `BootReceiver` uses `app.container.appScope` and delegates to `BootDeliveryRestorer`
(preferences + controller), which is unit tested. `LOCKED_BOOT_COMPLETED` is not handled because
credential-encrypted DataStore is unavailable before first unlock; documented in `RISKS.md`.

### A-10 (Medium) — report accuracy

`PHASE_3_REPORT.md`, `PLAN.md`, this report and `PHASE_3_FINAL_AUDIT.md` were corrected. The audit
report keeps the original FAIL text and appends remediation evidence. Cold-start, banner and clock
claims now match tests.

### A-11 … A-14 (Low) — accepted/documented

- Moot periodic backoff: documented; the next period is the retry.
- Notification ID/PendingIntent hash collisions: accepted bounded risk.
- Denied notification permission hides the persistent/paused notice: status remains in Settings.
- Double start request on startup: `onStartCommand` cancels the previous engine; benign.

---

## 3. Files changed

**Main:** `model/DeliveryMode.kt`; `delivery/DeliveryStatus.kt` (new);
`delivery/InstantForegroundService.kt`; `delivery/NftyNotifier.kt`;
`delivery/DeliveryController.kt`; `delivery/BootReceiver.kt`; `delivery/BootDeliveryRestorer.kt`
(new); `delivery/InstantTimeoutFallback.kt` (new); `AppContainer.kt`; `MainViewModel.kt`;
`MainActivity.kt`; `data/preferences/AppPreferences.kt`;
`data/repository/MessageRepository.kt`; `data/repository/NftyRepository.kt`;
`data/local/Daos.kt`; `ui/screens/SettingsScreen.kt`; `ui/components/NftyTopBar.kt` (fix retained).

**Build:** `app/build.gradle.kts`, `gradle/libs.versions.toml` — test-only
`androidx.work:work-testing:2.10.1`.

**Tests:** new delivery/model tests listed above; updated `MainViewModelTest`, `NftyNotifierTest`,
`MessageDaoTest`, `NftyRepositoryTest`, `Fakes.kt`, `NiftyTopBarClockTest`.

**Docs:** `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md` (appended), `PHASE_3_FINAL_AUDIT.md`,
`PLAN.md`, `PROGRESS.md`, `DECISIONS.md`, `RISKS.md`, `TODO.md`, `CHANGELOG.md`,
`OPENCODE_HANDOFF.md`.

No files were deleted. No Room schema change. No secrets added.

---

## 4. Verification

```
./gradlew testDebugUnitTest --rerun-tasks   # exit 0; 173 tests, 0 failures, 2 skipped
./gradlew assembleDebug --rerun-tasks       # exit 0; APK 19,557,690 bytes
./gradlew lint --rerun-tasks                # exit 0; 0 errors, 56 warnings
```

The two skips are the pre-existing AndroidKeyStore round-trip cases in `SecretStoreTest`.
No test was deleted, weakened, or newly skipped. Test count grew 151 → 173.

---

## 5. Remaining conditions

1. Real-device matrix: notifications grant/deny/revoke, Instant live/Doze/6 h timeout, Battery
   Saver cadence, mode switching under load, reboot, process death, network transitions, lock-screen
   privacy, OEM battery managers.
2. Notification actions (mark read/save) remain deferred by owner decision.
3. Instant remains best-effort; no mode guarantees delivery.
