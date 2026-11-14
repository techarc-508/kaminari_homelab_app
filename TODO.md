# TODO — Kaminari Nfty V1

**Updated:** 2026-11-12, after the Freebuff Workstream B Phase 0 reconciliation.
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

## Phase 5 — low-end/E-Ink (Complete — PASS WITH CONDITIONS)

- [x] Dedicated monochrome E-Ink profile (`ThemeSetting.EINK`) with high-contrast tokens.
- [ ] Vendor tuning only after the Boox model/Android version/firmware is recorded.
- [ ] Device/emulator/Boox-specific validation remains pending.

## Phase 6 — administration/security (Complete — PASS WITH CONDITIONS)

- [x] ntfy administration client for documented supported operations.
- [x] Separate trusted PVE gateway with allowlisted actions, authorization and audit.
- [x] Real gateway executor integration; PVE credentials remain server-side.
- [ ] Live server/gateway validation pending.

## Phase 7 — integration hardening (Complete — PASS WITH CONDITIONS)

- [x] Gateway path parameter validation.
- [x] Android gateway response size limit.
- [x] ntfy admin client HTTP timeouts.
- [x] Security audit report.
- [x] Integration test report.
- [x] Full Android verification (tests 354/0/0/2; debug build OK; lint 0 errors; gateway 9/1).
- [ ] Live gateway/device/validation.
- [ ] Production signing key setup (test-only release key in use).

## Phase 8 — Stage 1 final verification (Complete — PASS WITH CONDITIONS)

- [x] Retrospectively audit Phases 5–7.
- [x] Re-run Android tests (361/0/0/2).
- [x] Re-run debug build.
- [x] Re-run lint (0 errors).
- [x] Re-run gateway tests (9/0).
- [x] Add Stage 1 controlled integration tests for gateway executor and ntfy admin client.
- [x] STAGE_1_FINAL_VERIFICATION_REPORT.md.
- [ ] Live server/device/Boox validation.
- [ ] Production signing key setup (test-only release key in use).

## Phase 8 — Stage 2 release preparation (Complete — PASS WITH CONDITIONS)

- [x] Inspect release configuration.
- [x] Version/release identity decision.
- [x] Prepare production build config.
- [x] Signing readiness and secure owner key generation procedure.
- [x] APK/release artifact strategy and commands.
- [x] Release dependency/security review.
- [x] RELEASE_PREPARATION_REPORT.md, SIGNING_SETUP_GUIDE.md, RELEASE_BUILD_GUIDE.md.
- [x] Update TODO/PLAN/PROGRESS/CHANGELOG.
- [ ] Production signing key generated and backed up by owner.
- [ ] Signed release build verified.

## Phase 8 — Stage 3 gateway deployment package (Complete — PASS WITH CONDITIONS)

- [x] Deployment architecture documented.
- [x] Production configuration template (.env.example).
- [x] systemd unit and reverse-proxy example.
- [x] Gateway security, ACL, deployment, operations, backup/restore docs.
- [x] Non-deploying validate/health/backup/restore/rotate-token scripts.
- [x] Controlled validation pass; no infrastructure changed.
- [ ] Real deployment by owner.

## Phase 8 — Stage 4 ntfy infrastructure discovery & integration (Complete — PASS WITH CONDITIONS)

- [x] Discover existing ntfy infrastructure.
- [x] NTFY_EXISTING_INFRASTRUCTURE_AUDIT.md.
- [x] NTFY_TOPIC_REUSE_AND_INTEGRATION_PLAN.md.
- [x] NTFY_APP_INTEGRATION_AUDIT.md.
- [x] Rebuild/verify debug APK and copy to Desktop.
- [x] APK_DESKTOP_DELIVERY_REPORT.md.
- [x] NTFY_STAGE_4_VALIDATION_REPORT.md.
- [x] Live ntfy reachability/topic verification: health 200; `KAMINARI-FUJI-Homelab` + `kaminari-onboarding-test`; auth disabled, no admin/ACL.
- [ ] Production signing key setup (test-only release key in use).

## Phase 8 — Stage 4.1 final APK and configuration verification (Complete — PASS WITH CONDITIONS)

- [x] Confirm HEAD and working tree.
- [x] Run Android tests (361/0/0/2) and gateway tests (9/0).
- [x] Rebuild/verify debug APK.
- [x] Inspect package, versionName, versionCode, signing.
- [x] Copy verified APK to Desktop and SHA-256-match.
- [x] Report hostname findings.
- [x] STAGE_4_1_FINAL_VERIFICATION_REPORT.md.
- [x] Owner confirms canonical production ntfy hostname: `https://ntfy.rplab.qzz.io` (old `nfty` is 502).
- [x] Live ntfy reachability/topic verification: health 200; `KAMINARI-FUJI-Homelab` + `kaminari-onboarding-test`; auth disabled, no admin/ACL.
- [ ] Production signing key setup (test-only release key in use).

## Phase 8 — Stage 5 release engineering and publication preparation (Complete — PASS WITH CONDITIONS)

- [x] Inspect current release config, signing, backup, data-extraction, and network security posture.
- [x] R8/minification review.
- [x] Android backup and data-extraction policy review.
- [x] Network security review.
- [x] Release signing final guide.
- [x] Release build validation.
- [x] Stage 5 report, `R8_MINIFICATION_REVIEW.md`, `ANDROID_BACKUP_AND_DATA_EXTRACTION_POLICY.md`, `NETWORK_SECURITY_REVIEW.md`, `RELEASE_SIGNING_FINAL_GUIDE.md`, `RELEASE_BUILD_VALIDATION.md`.
- [x] Re-run Android tests and gateway tests.
- [x] Rebuild debug APK.
- [x] Produce test-only signed release APK for owner local install testing.
- [x] Confirm package `com.rplab.kaminarinfty` from binary manifest.
- [x] Document tool limitation: VM lacked `apksigner`/`aapt2`/`apktool`; `keytool -printcert -jarfile` reported `Not a signed jar file`, so full signature verification still depends on `apksigner` on a machine with Android SDK build tools.
- [x] Staged two target Desktop APKs: `/home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk` (debug) and `/home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk` (test-only signed release); both SHA-256-matched to `app/build/outputs/apk/*/app-*-apk`.
- [ ] Owner installs and tests `KaminariNfty-debug.apk` and `KaminariNfty_final_release.apk` on physical devices; both test variant paths must be run, not skipped.
- [x] Live ntfy reachability/topic verification: health 200; `KAMINARI-FUJI-Homelab` + `kaminari-onboarding-test`; auth disabled, no admin/ACL.
- [ ] Production signing key setup (test-only release key in use).

## Phase 7 — release (NOT authorised)

- [ ] Reproducible release signing.
- [ ] Final validation, `dist/KaminariNfty-v1.0.0.apk`, checksums, release notes.

## Final pass — 2026-11-11

- [x] Restore full project history and merge remote README without force-push.
- [x] Sanitize unpublished test-signing passwords; gitleaks history/tree 0 findings.
- [x] Live ntfy audit and canonical URL.
- [x] PVE least-privilege `<gateway-user>@pve!<token-id>` token + ACL.
- [x] Documentation library, owner guide, device checklist.
- [x] Final debug and test-only release APKs copied to Desktop with checksums.
- [x] Public GitHub publication.
- [ ] Physical device tests (`OWNER_DEVICE_TESTING_CHECKLIST.md`).
- [ ] Production signing key.
- [ ] Gateway deployment/TLS.
- [ ] ntfy authentication enablement and app token.

## Ntfy redesign Stage 1 — 2026-11-12

- [x] Exact PVE backup archive inventory and deletion manifest.
- [x] Storage/SMART/ZFS capacity diagnosis.
- [x] CT104/VM200 backup failure diagnosis.
- [x] PVE notification HTTP 400 cause and repair.
- [x] 22-topic catalogue without `kf-builds`.
- [x] ACL matrix, admin/gateway authorization model and Level 4 spec.
- [x] Music, stickers and news notification contracts.
- [x] Freebuff implementation master plan.
- [ ] Owner confirms exact archive deletion manifest (`Scope A` or `A+B`).
- [ ] Owner selects backup target Option A, B or C.
- [ ] Complete six-guest backup baseline and verify every archive.
- [ ] Add a verified off-site backup destination.
- [ ] Freebuff: identity/ACL provisioning, 22 topics, monitoring, gateway, Android control centre.

## Freebuff Workstream B — Phase 0 reconciliation (2026-11-12)

- [x] Read all existing plans, phase reports, discovery documents and approved specifications.
- [x] Inspect the Android, gateway, Room, delivery and command code paths.
- [x] Re-probe the live ntfy endpoint read-only (health, config, stats, DNS, TLS).
- [x] Re-run the baseline test suites (Android 361/0/0/2; gateway 9 passed).
- [x] Produce `FREEBUFF_PHASE0_RECONCILIATION.md`.
- [x] Produce `LIVE_NTFY_INVENTORY.md`.
- [x] Produce `TOPIC_MIGRATION_MATRIX.md`.
- [x] Produce `GATEWAY_GAP_ANALYSIS.md`.
- [x] Produce `ANDROID_WEB_GAP_ANALYSIS.md`.
- [x] Record findings C-01…C-22 and pending owner decisions P-01…P-09.
- [ ] **Owner review of Phase 0 and explicit approval before Phase 1.**

### Phase 0 findings that must be resolved before implementation

- [ ] P-01: confirm `kf-` supersedes `rplab-` in every document, including the discovery set and priority standard.
- [ ] P-02: resolve the Android/gateway token split (ntfy ACLs are per user, not per token).
- [ ] P-03: decide whether an admin-capable `NtfyAdminClient` may ship in the APK.
- [ ] P-04: choose a gateway deployment host/TLS path, or keep the command centre mock-only.
- [ ] P-05: approve a durable local audit store; ntfy is not an audit system of record.
- [ ] P-06: replace nonce-echo approval with real step-up reauthentication.
- [ ] P-07: resolve `kf-diary` write ownership (`kf_stickers` grant vs stickers contract).
- [ ] P-08: decide `kf-iot` ACL now, or create it denied until a real producer exists.
- [ ] P-09: add legacy-test-topic access for `kaminari_app` to the migration/ACL test plan.

### Phase 1 (safe, no production infrastructure) — COMPLETE, PASS WITH CONDITIONS

- [x] Typed `kf-` topic catalogue and pipeline payload models.
- [x] Room `MIGRATION_2_3` for topic category/priority, tested from `1.json` and `2.json`.
- [x] Topic-mapping and ACL-expectation contract tests.
- [x] Music/news/stickers payload schemas and validation tests.
- [x] Fix `COMMAND_REPLY_TOPIC` to `kf-command-result` with a contract test and legacy allowance.
- [x] Align the Android and gateway action catalogues (14 actions).
- [x] Remove `NtfyAdminClient` from production sources; keep it as a test fixture (P-03 Option B).
- [x] Add the honest gateway-backed administration contract and `ADMIN` destination.
- [x] Stop for owner review after Phase 1.

### Phase 1 conditions carried into Phase 2

- [ ] Deploy on the P-04 host/TLS path or explicitly remain mock-only.
- [ ] Implement real step-up authentication (P-06); keep mutations disabled until verified.
- [ ] Make audit and idempotency durable (P-05).
- [ ] Implement typed administration operations behind `GatewayAdminService`.
- [ ] Remove or fully type the raw PVE read routes (B-21).
- [ ] Add a physical-device Room v2→v3 upgrade test (B-20).
- [ ] Seed the approved catalogue locally without changing live topics.
- [ ] Obtain explicit owner authorisation before any Phase 2 work.

## Backup Workstream A — Phase A (2026-11-12)

- [x] Read project documentation and existing Stage 1 audit findings.
- [x] Inventory actual devices, storage, guests, archives and current backup jobs.
- [x] Measure latest per-guest generation and capacity.
- [x] Produce `BACKUP_DEVICE_DISCOVERY.md`.
- [x] Produce `BACKUP_GUEST_INVENTORY.md`.
- [x] Produce `BACKUP_CAPACITY_MODEL.md`.
- [x] Produce `BACKUP_ARCHITECTURE_PROPOSAL.md`.
- [x] Produce `BACKUP_RISK_REGISTER.md`.
- [x] Produce `BACKUP_PHASE_A_REPORT.md`.
- [ ] **Owner decision:** 2 vs 3 SSD generations (and 20 % reserve exception).
- [ ] **Owner decision:** HDD quota 220–240 GiB or transient one-generation copy.
- [ ] **Owner decision:** SSD carve-out size / future `fast-data` trade-off.
- [ ] **Owner decision:** application-aware Immich/Docker backup and CT104 bind-mount coverage.
- [ ] **Owner decision:** encryption key custody for host configuration.
- [ ] **Owner decision:** restore-drill target (new VMID/isolated bridge).
- [ ] **Owner decision:** old 12 archives deletion (Scope A or A+B) — still not authorized.
- [ ] Phase B storage design — not started.
