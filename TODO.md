# TODO — Kaminari Nfty V1

**Updated:** 2026-11-10, after Stage D completed and was checkpointed.
Phase 4 is authorised through **Stage D**; Stages E–F require owner approval at the Stage D gate.

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

## Phase 4 — Stage D complete; Stages E–F await approval

- [x] Stage 0: freeze Room v1 schema; export `1.json`; KSP schema output enabled.
- [x] Stage A: Room v2 entities, DAOs, `MIGRATION_1_2`, migration test, DAO state-transition tests, schema `2.json`.
- [x] Stage A.1: profile-scoped DAO operations, fail-closed outbox parsing, retention policy, migration assertions — checkpoint `49e3159`.
- [x] Owner review of `PHASE_4_STAGE_A_REPORT.md` and approval to start Stage B.
- [x] Stage B: shared `NtfyAuth`, `PublishValidator`, JSON publish client + MockWebServer tests — see `PHASE_4_STAGE_B_REPORT.md`.
- [x] Owner review of `PHASE_4_STAGE_B_REPORT.md` and approval to start Stage C.
- [x] Stage C: `OutboxRepository` state machine, `ActivityRepository`, receive-event transaction, manual retry, interrupted-send recovery, retention — see `PHASE_4_STAGE_C_REPORT.md`.
- [x] Owner review of `PHASE_4_STAGE_C_REPORT.md` and approval to start Stage D.
- [x] Stage D: `PublishViewModel`/`ActivityViewModel`, six-destination navigation, Saved filter, profile switch/preview — see `PHASE_4_STAGE_D_REPORT.md`.
- [x] Owner review of `PHASE_4_STAGE_D_REPORT.md` and approval to start Stage E.
- [x] Owner review of `PHASE_4_STAGE_E_REPORT.md` and approval to start Stage F.
- [ ] Owner review of `PHASE_4_STAGE_F_REPORT.md` (Phase 4 complete; do not start Phase 5).
- [x] Stage E: `rplab.command.v1` envelope, policy, repository, `MockCommandExecutor`, CommandPanel — see `PHASE_4_STAGE_E_REPORT.md`.
- [x] Stage F: full gate, independent QA, final `PHASE_4_REPORT.md` — see `PHASE_4_STAGE_F_REPORT.md`.
- [x] Owner authorization of Phase 4 scope (2026-11-09); Stage A only was authorised in that task.
- [x] Owner acceptance of Stage B and explicit authorization of Stage C (2026-11-10).
- [x] Owner acceptance of Stage C and explicit authorization of Stage D (2026-11-10).

## Deferred (owner decision D-05)

- [ ] Notification actions: mark read, save/star.
- [ ] Optional FCM/UnifiedPush feasibility spike.

## Future polish (not Phase 4 blockers)

- [ ] Collision-safe notification IDs / PendingIntent request codes.
- [ ] Remove or implement the unused periodic work backoff configuration.
- [ ] Add a full service integration test for terminal stream failure once a profile/mock-stream harness exists.
- [ ] Surface Battery Saver repeated-failure diagnostics in Settings.
- [ ] `LOCKED_BOOT_COMPLETED` support if a credential-free boot path becomes necessary.

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
