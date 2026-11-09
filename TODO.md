# TODO — Kaminari Nfty V1

**Updated:** 2026-11-09, after Phase 3 remediation.
Work must stay phase-scoped; Phase 4 is not authorised.

## Phase 3 closure (owner/device required)

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
- [ ] Owner sign-off on the two-mode UI and legacy `SAVED` → `BATTERY_SAVER` migration.
- [ ] Owner approval before Phase 4.

## Deferred (owner decision D-05)

- [ ] Notification actions: mark read, save/star.
- [ ] Optional FCM/UnifiedPush feasibility spike.

## Future polish (not Phase 3 blockers)

- [ ] Collision-safe notification IDs / PendingIntent request codes.
- [ ] Remove or implement the unused periodic work backoff configuration.
- [ ] Add a full service integration test for terminal stream failure once a profile/mock-stream harness exists.
- [ ] Surface Battery Saver repeated-failure diagnostics in Settings.
- [ ] `LOCKED_BOOT_COMPLETED` support if a credential-free boot path becomes necessary.

## Phase 4+ (NOT authorised)

- [ ] Compose/publish screen and send outbox.
- [ ] Activity timeline.
- [ ] Command centre with trusted PVE gateway.
- [ ] Dedicated E-Ink profile (after Boox model/firmware is known).
- [ ] ntfy administration grounded in verified server capabilities.
