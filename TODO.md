# TODO — Kaminari Nfty V1

**Updated:** 2026-11-09, after Phase 3 acceptance and Phase 4 planning.
Phase 4 is **not authorised**; these are proposed tasks only.

## Phase 3 closure (owner/device required)

Phase 3 is accepted as **PASS WITH CONDITIONS**. These conditions are still open:

- [ ] Run the device matrix in `PHASE_3_REPORT.md` §6:
  - [ ] Android 13/14/15/16 notification permission grant / deny / revoke.
  - [ ] Instant live delivery with screen off; persistent notification.
  - [ ] Android 15 6-hour `dataSync` cutoff: service stops without a crash, Battery Saver fallback appears.
  - [ ] Terminal auth failure while running: service stops, notification/banner shows the real reason.
  - [ ] Battery Saver cadence, Doze deferral, duplicate-free 24 h run.
  - [ ] Mode switching under message load; no loss or double notification.
  - [ ] Reboot with each mode; Instant resume notice; Battery Saver rescheduling.
  - [ ] Process death / `START_STICKY` restart.
  - [ ] Network loss and recovery in service and worker.
  - [ ] Lock-screen privacy on a real device.
  - [ ] OEM battery managers (Xiaomi/Samsung/Oppo).
  - [ ] Cold-start and warm notification taps.
- [ ] Re-probe `https://nfty.rplab.qzz.io/v1/health` when the origin is up; run a real receive/publish smoke test.
- [ ] Owner sign-off on the two-mode UI and legacy `SAVED` → `BATTERY_SAVER` migration.
- [ ] Verify the Android Keystore round-trip on hardware (the two skipped tests).

## Phase 4 — proposed, NOT authorised

- [ ] Stage 0: freeze Room v1 schema; add test-only `room-testing`.
- [ ] Stage A: Room v2 entities, DAOs, `MIGRATION_1_2`, migration test.
- [ ] Stage B: shared `NtfyAuth`, `PublishValidator`, JSON publish client + MockWebServer tests.
- [ ] Stage C: `ActivityRepository`, receive-event transaction, `OutboxRepository` state machine.
- [ ] Stage D: `PublishViewModel`/`ActivityViewModel`/`CommandViewModel`, five-destination navigation, Saved filter, profile switch/preview.
- [ ] Stage E: `rplab.command.v1` envelope, policy, repository, `MockCommandExecutor`, CommandPanel.
- [ ] Stage F: full gate, independent QA, `PHASE_4_REPORT.md`.
- [ ] Owner approval of `PHASE_4_ARCHITECTURE.md` and `PHASE_4_IMPLEMENTATION_PLAN.md` before any code.

## Deferred (owner decision D-05)

- [ ] Notification actions: mark read, save/star.
- [ ] Optional FCM/UnifiedPush feasibility spike.

## Future polish (not Phase 4 blockers)

- [ ] Collision-safe notification IDs / PendingIntent request codes.
- [ ] Remove or implement the unused periodic work backoff configuration.
- [ ] Add a full service integration test for terminal stream failure once a profile/mock-stream harness exists.
- [ ] Surface Battery Saver repeated-failure diagnostics in Settings.
- [ ] `LOCKED_BOOT_COMPLETED` support if a credential-free boot path becomes necessary.
- [ ] Fix the one-line `when` formatting in `NftyRepository.kt:187` and add a FORBIDDEN-mapping assertion (done during Phase 4 Stage B if touched; otherwise here).

## Phase 5 — low-end/E-Ink (NOT authorised)

- [ ] Dedicated monochrome E-Ink profile (no animation, gradients or shadows; static status; manual refresh).
- [ ] Vendor tuning only after the Boox model/Android version/firmware is recorded.

## Phase 6 — administration/security (NOT authorised)

- [ ] ntfy administration grounded in verified server capabilities.
- [ ] Separate trusted PVE command gateway with allowlisted actions, independent authorization and audit.
- [ ] Real executor integration; never embed PVE credentials or arbitrary shell in the APK.

## Phase 7 — release (NOT authorised)

- [ ] Reproducible release signing.
- [ ] Final validation, `dist/KaminariNfty-v1.0.0.apk`, checksums, release notes.
