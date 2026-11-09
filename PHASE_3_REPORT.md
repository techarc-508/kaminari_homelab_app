# Phase 3 Report — Notification and background delivery

**Date:** 2026-11-09 (remediated after the independent FAIL audit)
**Scope:** Phase 3 only (notifications, Instant mode, Battery Saver mode, mode controls, lifecycle handling).
**Verdict:** **PASS WITH CONDITIONS** — all automated checks are green after remediation; the real-device
matrix in §6 is still outstanding.

---

## 1. Phase and objective

Implement the owner-locked delivery requirements on top of the Phase 1/2 architecture, without
rebuilding it:

- Android notification channels and the Android 13+ runtime permission flow.
- Deep links from notifications into the matching message, with lock-screen privacy.
- Both owner-selected modes — **Instant** (visible foreground connection) and **Battery Saver**
  (scheduled, explicitly inexact catch-up) — with explicit switching and visible trade-offs.
- Lifecycle handling: process death (mode persisted), reboot, network transitions, and the Android
  15+ foreground-service time limit.
- No claim of guaranteed instant background delivery.

Out of scope and not implemented: FCM/UnifiedPush spike (optional), publishing UI, E-Ink profile, ntfy
administration, PVE command gateway (Phase 4+).

## 2. What changed

**Delivery modes** are a persisted, user-selectable enum with exactly two values:
`INSTANT` and `BATTERY_SAVER`. The pre-remediation third value `SAVED` (foreground-only) was removed;
`DeliveryMode.fromPersisted` migrates a stored `SAVED` value to `BATTERY_SAVER`, and fresh installs
default to `BATTERY_SAVER`. Saved *messages* are untouched and remain in the Saved tab.

**Instant mode** hands the live stream to a `dataSync` foreground service with a persistent
low-importance notification. The service re-reads the persisted mode, profile and topics on start,
performs a bounded catch-up, then opens the live subscription. It publishes its real lifecycle
through a process-wide `DeliveryStatusStore`: starting, catching up, connecting, live, reconnecting,
auth failed, permission denied, timed out, or failed. The ongoing notification text is derived from
that state, so it can no longer say "live delivery" over a dead stream. The ViewModel suppresses its
own stream and its own notifications in this mode and reads the service-owned status instead of
reporting its stopped repository as offline.

**Android 15+ foreground-service limit:** `InstantForegroundService.onTimeout(startId, fgsType)`
records `TIMED_OUT`, posts a plain notice, stops the foreground notification, persists
`BATTERY_SAVER`, schedules catch-up, and calls `stopSelf(startId)` within the platform deadline.
The 6-hour-per-24-hour `dataSync` budget is a platform restriction and is **not** avoided, extended,
or reset by the app; it resets only when the app returns to the foreground. There is no crash path
from an unhandled timeout anymore.

**Terminal stream failures:** `NftyRepository` now exposes `isTerminal` (terminal auth/forbidden
error, no topics, or the bounded unproductive-retry limit). The service observes it and
`connectionState`, updates the status store, notifies the user with the real reason, and stops
itself. Cancellation and service shutdown still close the stream and the service-owned repository.

**Foreground-start failure:** if `startForeground()` is refused, the service records `FAILED` with a
readable explanation, posts a notice when notifications are permitted, and stops. The ViewModel
surfaces the status in its banner; it no longer claims Instant is active merely because the start
request was accepted.

**Notification deep links:** cold starts no longer consume the message ID against an empty in-memory
list. `openMessageFromNotification` waits for the active profile, checks the loaded window, then
loads the message directly from Room with `MessageRepository.getById`. Missing or deleted messages
produce an honest banner instead of an empty detail screen; warm `onNewIntent` taps behave the same.

**Reboot:** `BootDeliveryRestorer` is the tested decision unit behind `BootReceiver`; it uses the
app-owned scope rather than an unmanaged one. Battery Saver is re-scheduled; Instant posts a resume
notice and does not attempt the forbidden boot-time `dataSync` start.

**UI:** Settings shows exactly two delivery modes with trade-offs, the live/degraded status for
Instant, the notification-blocked row, and the notification-settings action.

**Pre-existing clock defect (preserved fix):** `NftyTopBar` sleeps to the next second boundary
instead of requesting a frame per frame. The vacuous test was replaced with one that renders the
real composable, asserts the injected time, asserts that sub-second time does not change the text,
and asserts the composition reaches idle (the old loop threw `AppNotIdleException`).

**Lint fixes retained:** explicit `POST_NOTIFICATIONS` checks plus `SecurityException` handling
around `notify()`, an API-26 guard with an app-details fallback for notification settings on API
24–25, and removal of a redundant activity label.

## 3. Files added / modified / deleted

**Added (main):**

- `model/DeliveryMode.kt` (rewritten: two modes + legacy migration)
- `delivery/DeliveryStatus.kt` (status model + process-wide store)
- `delivery/NftyNotifier.kt`, `delivery/DeliveryController.kt`
- `delivery/InstantForegroundService.kt`
- `delivery/BatterySaverWorker.kt`
- `delivery/BootReceiver.kt`, `delivery/BootDeliveryRestorer.kt`
- `delivery/InstantTimeoutFallback.kt`
- `res/drawable/ic_notification.xml`

**Added (tests):**

- `delivery/NftyNotifierTest.kt` (8)
- `delivery/DeliveryStatusTest.kt` (5)
- `delivery/InstantTimeoutFallbackTest.kt` (1)
- `delivery/BootDeliveryRestorerTest.kt` (2)
- `delivery/InstantForegroundServiceTest.kt` (1)
- `delivery/BatterySaverWorkerTest.kt` (2)
- `model/DeliveryModeTest.kt` (4)
- `ui/components/NiftyTopBarClockTest.kt` (rewritten, 2)
- additions to `MainViewModelTest`, `MessageDaoTest`, `NftyRepositoryTest`, `testing/Fakes.kt`

**Modified:** `AndroidManifest.xml`, `AppContainer.kt`, `MainActivity.kt`, `MainViewModel.kt`,
`data/preferences/AppPreferences.kt`, `data/repository/MessageRepository.kt`,
`data/repository/NftyRepository.kt`, `data/local/Daos.kt`, `ui/screens/SettingsScreen.kt`,
`ui/components/NftyTopBar.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml` (test-only
`androidx.work:work-testing`).

**Deleted:** none. No Room schema change, no migration required.

## 4. Architecture and API decisions

- **Single stream owner:** in Instant mode the service owns the stream. The ViewModel observes
  `DeliveryStatusStore`; it never opens a duplicate subscription to fix status reporting.
- **Honest state machine:** `DeliveryStatus` is written by the service and mapped to the existing
  `AuthState` values for the Inbox indicator. Terminal states carry a sanitised reason.
- **Timeout fallback:** platform-forced switch to Battery Saver is explicit, persisted, and
  scheduled even if no ViewModel is alive. It does not try to extend the `dataSync` budget.
- **Deep links by ID:** Room is the source of truth; the in-memory window is only a cache.
- **Only two delivery modes:** the legacy `SAVED` selection migrates to Battery Saver, preserving
  background delivery rather than silently leaving the user with none.
- **Test dependency:** `androidx.work:work-testing:2.10.1` is test-only, matches the production
  WorkManager version, and is used to construct the real worker; it does not enter the APK.

## 5. Tests run — exact commands and outcomes

Run from the repository root (`kaminari_homelab_app-main`), JDK 21, AGP 9.1.1, Kotlin 2.2.10,
compileSdk 36, targetSdk 36, minSdk 24. Each command was run separately with `--rerun-tasks`:

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL**, exit 0 — **173 tests, 0 failures, 0 errors, 2 skipped** (1 m 07 s, 32 tasks executed) |
| `./gradlew assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL**, exit 0 — `app/build/outputs/apk/debug/app-debug.apk`, 19,557,690 bytes (39 s, 38 tasks) |
| `./gradlew lint --rerun-tasks` | **BUILD SUCCESSFUL**, exit 0 — **0 errors, 56 warnings** (1 m 13 s, 30 tasks) |

The two skips are the pre-existing `SecretStoreTest` AndroidKeyStore cases; no test was deleted,
weakened, or newly skipped. The suite grew from 151 to 173 tests.

New coverage: timeout phase and stop; timeout fallback persistence/scheduling; boot restoration for
both modes; worker mode-guard and no-profile paths; notifier phase text and stopped notice; status
mapping and sequence; legacy mode migration; cold-start deep-link lookup with an empty in-memory
window and a genuinely missing target; repository terminal flag for terminal auth errors; real
clock second-boundary/idle behaviour; Inbox state reflecting the service rather than the stopped
ViewModel stream.

## 6. Manual / device verification still required

No device or emulator was reachable (`adb` is not installed). These remain **unverified**:

1. Android 13/14/15/16: grant, deny, mid-session revoke `POST_NOTIFICATIONS`; Settings status and
   the notification-settings action (API 26+ and API 24–25 fallbacks).
2. Instant: screen off / backgrounded live delivery and persistent notification; **observe the
   Android 15 6-hour `dataSync` cutoff** and confirm `onTimeout` stops without a crash and the
   Battery Saver fallback appears.
3. Instant: wrong token / revoked permission while running; confirm the service stops and the
   notification and banner show the real reason (no false "live").
4. Battery Saver: 15-minute cadence, Doze deferral, duplicate-free repeated runs, 24 h lag.
5. Mode switching under message load: no loss, no double notification.
6. Reboot with each mode persisted; Instant resume notice; Battery Saver rescheduling.
7. Process death (`am kill`, "Don't keep activities") in Instant, including `START_STICKY` restart.
8. Network loss/recovery in the service and worker.
9. Lock-screen privacy: body hidden locked, visible unlocked.
10. OEM battery managers (Xiaomi/Samsung/Oppo): document variance, do not promise delivery.
11. Cold-start deep link and warm `onNewIntent` tap on hardware.

## 7. Security and privacy review

- Notification content is `VISIBILITY_PRIVATE`; the public lock-screen version shows only `#topic`
  and "New notification", never the body.
- No credentials in intents or notifications. Deep links carry only the message ID; the detail
  screen loads from Room.
- Service and receiver are `exported="false"`; the boot receiver reacts only to `BOOT_COMPLETED`.
- `PendingIntent`s are `FLAG_IMMUTABLE`.
- `notify()` is guarded by an explicit permission check and a `SecurityException` catch.
- Terminal details shown to the user come from the app's own status mapping and the server's error
  message; no local secrets are included.
- No new network endpoints, no admin capability, no arbitrary command execution.

## 8. Performance and memory implications

- Instant keeps one stream, one socket and one foreground service — the intended, visible cost.
- Battery Saver wakes roughly every 15 minutes and runs one bounded 60 s catch-up, then closes its
  repository.
- Status updates are a single `StateFlow`; no polling or extra timers were added.
- The clock fix removes ~60 UI-thread frame callbacks per second when visible.
- No new in-memory caches; Room remains the source of truth.

## 9. Known issues and risks

| Severity | Issue |
|---|---|
| High (platform, documented) | Android 15+ caps `dataSync` foreground services at 6 h per 24 h. Instant is handled, not immune: the service stops before the fatal deadline and falls back to Battery Saver. A reboot still cannot start Instant until the app is opened. |
| Medium | Battery Saver latency is inherently inexact (15-minute WorkManager floor, Doze). Documented; needs measurement. |
| Medium | Notification IDs use a 30-bit hash plus offset; a collision overwrites an earlier notification (history is unaffected). |
| Medium | Notification actions beyond "open" (mark read/save) are not implemented; deferred by the owner. |
| Medium | Real-device behaviour (Doze, OEM killers, reboot, notification rendering) is not yet verified. |
| Low | A denied `POST_NOTIFICATIONS` hides the persistent and paused notices on Android 13+; status is still visible in Settings. |
| Low | Boot handling covers `BOOT_COMPLETED` only (not `LOCKED_BOOT_COMPLETED`), because credential-encrypted DataStore is unavailable before unlock. |
| Low | No FCM/UnifiedPush spike (optional per PLAN). |

## 10. Rollback / recovery

- All Phase 3 changes are additive; the removal of `SAVED` is a persisted-value mapping, not a data
  migration. Older builds treat the stored `BATTERY_SAVER`/`INSTANT` strings normally and default
  unknown values to their own default.
- No Room schema or migration was touched.
- WorkManager's unique work is cancelled/scheduled on mode changes; `adb shell dumpsys jobscheduler`
  can confirm residual jobs.
- To revert code: restore the Phase 2 commit `5d00827` and remove the `delivery/` additions. This
  also reverts the timeout/deep-link/status fixes and is not recommended without the owner.

## 11. Recommendation

**PASS WITH CONDITIONS.** All automated gates are green and the audit's Critical/High code findings
are addressed with regression coverage. The conditions are the device tests in §6, especially the
Android 15 timeout and OEM power-management observations.

## 12. Approval needed before Phase 4

1. Accept the remediation and the documented platform limits (Instant = best-effort foreground,
   Battery Saver = inexact ~15 min, 6 h `dataSync` cap).
2. Accept the two-mode UI and the legacy `SAVED` → `BATTERY_SAVER` migration.
3. Accept that notification actions beyond "open message" are deferred.
4. Authorise Phase 4, or request device results first.

**Do not start Phase 4 without explicit approval.**
