# Phase 3 Acceptance — Kaminari Nfty V1

**Status:** ACCEPTED — PASS WITH CONDITIONS
**Decision date:** 2026-11-09
**Phase 3 checkpoint:** commit `72dbf36` (44 files, +3225/−43, no deletions)
**Repository HEAD at acceptance:** `2fe0ce6`
**Independent takeover audit:** `OPENCODE_TAKEOVER_REPORT.md`
**Phase 4:** not authorised by this document.

---

## 1. History is preserved, not rewritten

The audit trail remains exactly as it happened:

| Stage | Document | Verdict |
|---|---|---|
| Initial independent audit | `PHASE_3_AUDIT_REPORT.md` | **FAIL** (A-01…A-04 critical/important) |
| Remediation | `PHASE_3_REMEDIATION_REPORT.md` | Fixes implemented, re-tested |
| Re-audit | `PHASE_3_FINAL_AUDIT.md` | **PASS WITH CONDITIONS** |
| Owner acceptance | this document | **ACCEPTED — PASS WITH CONDITIONS** |

The original FAIL is not deleted, softened or re-labelled. It is the reason the
remediation exists and remains part of the record.

## 2. What was accepted

Phase 3 delivers the owner-locked two-mode notification system and closed the
four findings that blocked the original audit:

| Finding | Closure | Evidence |
|---|---|---|
| A-01 — Android 15 `dataSync` timeout crash | `InstantForegroundService.onTimeout` records `TIMED_OUT`, stops the service inside the deadline, falls back to Battery Saver | `InstantForegroundService.kt:114–137`; `InstantForegroundServiceTest.kt:19–33`; `InstantTimeoutFallbackTest.kt` |
| A-02 — false "live delivery" notification over a dead stream | Terminal auth/forbidden/retry-exhaustion stops the service and posts the real reason; `DeliveryStatusStore` is service-owned | `NftyRepository.kt:185–195` (`isTerminal`), `InstantForegroundService.kt:203–228`; `DeliveryStatusTest.kt` |
| A-03 — false offline UI / duplicate stream | UI observes the service-owned `DeliveryStatusStore`; it does not open a second stream | `MainViewModelTest.kt:543–565` (`startCalls == 0`) |
| A-04 — broken cold-start notification deep link | Deep link resolves by message ID from Room, independent of the in-memory window | `MainViewModel.kt` deep-link path; `MainViewModelTest.kt:522–539`; `MessageDaoTest.kt` |

Supporting properties accepted with the phase:

- Selectable modes are exactly **Instant** and **Battery Saver**. Legacy
  persisted `SAVED` migrates one-way to `BATTERY_SAVER`; fresh installs default
  to Battery Saver. Saved *messages* are unrelated and unaffected.
- Instant is best-effort foreground delivery; Battery Saver is inexact periodic
  catch-up (15-minute WorkManager floor, Doze). Neither is a delivery guarantee.
- Reboot handling covers `BOOT_COMPLETED` after first unlock only.
- No user data or source files were deleted during remediation.

## 3. Verification evidence

Independent takeover verification, each task re-run separately with
`--rerun-tasks` on 2026-11-09:

```text
./gradlew testDebugUnitTest --rerun-tasks
  exit 0 — 173 tests, 0 failures, 0 errors, 2 skipped
  (the two skips are the pre-existing Keystore round-trip tests)

./gradlew assembleDebug --rerun-tasks
  exit 0 — app-debug.apk, 19,557,690 bytes

./gradlew lint --rerun-tasks
  exit 0 — 0 errors, 56 warnings
```

The first combined test run died from a Gradle daemon OOM (7.8 GiB host, swap
exhausted). Running each task separately passes. This is a host-memory
constraint, not an app defect; record it whenever reproducing the gate.

The clock regression test is meaningful: it renders the real composable,
asserts the initial time, asserts no sub-second recomposition, asserts the
next-second update, and asserts the screen then goes idle.

## 4. Conditions of acceptance

These are **not** closed by the owner acceptance. No hardware validation may be
claimed until they are executed.

1. **No device or emulator validation.** `adb` and an emulator are unavailable on
   the build host. Doze, reboot, OEM battery managers, notification rendering,
   lock-screen privacy and the real Android 15 `dataSync` timeout are unobserved.
2. **Device matrix outstanding** (`PHASE_3_REPORT.md` §6): notification
   permission grant/deny/revoke; Instant with screen off; Android 15 six-hour
   cutoff; terminal auth failure while running; Battery Saver cadence and Doze
   deferral; duplicate-free 24 h run; mode switching under load; reboot per mode;
   process death; network loss/recovery; cold and warm notification taps;
   Xiaomi/Samsung/Oppo battery managers.
3. **Real ntfy server still unverified.** `https://nfty.rplab.qzz.io/v1/health`
   returned HTTP 502 (Cloudflare bad gateway) on 2026-11-08. TLS and DNS are
   fine; the origin is down. No real-server smoke test of receive/publish has
   run against the owner's instance.
4. **Keystore on hardware unverified.** The two skipped tests cover the real
   Android Keystore round-trip; they pass on a device when one is available.
5. **Release signing not reproducible.** The upload keystore and environment
   variables are absent; no release APK exists yet. That is Phase 7 work.

## 5. What this acceptance does and does not authorise

Authorised by acceptance:

- Phase 3 is closed as **PASS WITH CONDITIONS**.
- Phase 4 planning may be reviewed by the owner.

Not authorised:

- Phase 4 implementation. It requires a separate explicit approval of
  `PHASE_4_ARCHITECTURE.md` and `PHASE_4_IMPLEMENTATION_PLAN.md`.
- Release signing, publishing an APK, real PVE execution, ntfy administration
  or E-Ink device tuning.

## 6. Residual risks carried forward

The full register is `RISKS.md`. The conditions above map to R-01 (no device
validation), R-02 (Android 15 budget), R-04 (OEM killers), R-05 (inexact Battery
Saver), R-14 (server unverified) and R-15 (Keystore on hardware). None is
downgraded or closed by this acceptance.

## 7. Sign-off

- **Owner:** accepted Phase 3 as PASS WITH CONDITIONS (2026-11-09).
- **Builder:** Freebuff; Phase 3 remediation.
- **Independent auditor:** OpenCode takeover audit, 2026-11-09.
- **Next action:** owner review of the Phase 4 architecture and implementation
  plan; explicit approval required before any Phase 4 code is written.
