# Risk Register — Kaminari Nfty V1

**Last updated:** 2026-11-12 (Freebuff Workstream B Phase 0 additions, R-41…R-52)
Severity reflects user impact; state reflects what is verified.

| ID | Risk | Severity | State / mitigation |
|---|---|---|---|
| R-01 | **No real-device validation of background delivery.** Doze, OEM killers, reboot, notification rendering and the Android 15 timeout are unobserved. | High | Open. Device matrix in `PHASE_3_REPORT.md` §6. Do not claim hardware verification. |
| R-02 | **Android 15+ caps `dataSync` foreground services at 6 h per rolling 24 h.** Instant cannot run indefinitely. | High | Mitigated, not eliminated: `onTimeout` stops before the fatal deadline and migrates to Battery Saver; documented in the UI copy and report. Budget is neither extended nor reset by the app. |
| R-03 | **Terminal auth/permission failure stops delivery.** User must fix credentials. | High | Mitigated: service stops, posts the real reason, status/banner updated. No automatic credential recovery by design. |
| R-04 | **OEM battery managers may kill WorkManager/service delivery.** | High | Open and documented; no promise of delivery. Requires device testing per OEM. |
| R-05 | **Battery Saver timing is inexact** (15-minute WorkManager floor, Doze). | Medium | Accepted and visible in the mode copy. Measure on device. |
| R-06 | **Notification IDs/PendingIntent request codes use 30-bit hashes.** A collision can overwrite an earlier notification or tap target. | Low | Accepted; history is unaffected. Collision-safe IDs are future polish. |
| R-07 | **Denied `POST_NOTIFICATIONS` hides the persistent/paused notices** on Android 13+. | Low | Mitigated by the Settings status row and the notification-settings action; delivery itself still runs where Android permits. |
| R-08 | **Boot restore handles `BOOT_COMPLETED` only**, and only after first unlock. | Low | Accepted: credential-encrypted DataStore is unavailable earlier; documented. |
| R-09 | **Notification actions (mark read/save) are absent.** | Medium | Deferred by owner decision D-05; only "open message" is implemented. |
| R-10 | **No FCM/UnifiedPush fallback.** Delivery relies on the app process/WorkManager. | Medium | Optional spike in PLAN; not required for Phase 3. |
| R-11 | **Service terminal branch lacks an end-to-end test.** Repository flag, status mapping and notifier are tested; the full service reaction is inspected, not executed in a configured integration test. | Medium | Documented in `PHASE_3_FINAL_AUDIT.md`; add a service integration test when a mock profile/stream harness exists. |
| R-12 | **Moot WorkManager backoff.** `setBackoffCriteria` is configured but the worker never returns `retry()`. | Low | Accepted: the next scheduled period is the retry. Remove or document in a future cleanup. |
| R-13 | **E-Ink profile and vendor behavior unknown** until the Boox device arrives. | Medium (Phase 4+) | Deferred by owner decision D-04; do not invent capabilities. |
| R-14 | ~~Real ntfy server version / self-hosted smoke test unverified.~~ | — | **Closed 2026-11-11:** ntfy 2.28.0, health 200, publish/read-back on `kaminari-onboarding-test`. |
| R-15 | **Secrets/Keystore availability** (Robolectric skips the KeyStore round-trip; a device Keystore issue would block credentials). | High | Unit tests cover fallback behavior; verify credential save/load on hardware. |
| R-16 | **Publish ambiguity:** a timeout after the request may have reached the server; ntfy has no idempotency key, so a retry can duplicate a message. | High | Phase 4 design: `AMBIGUOUS` state, manual retry only, explicit duplicate-risk confirmation. Accepted, not eliminable client-side. |
| R-17 | ~~Real-server publish/receive unverified.~~ | — | **Closed 2026-11-11:** canonical `ntfy.rplab.qzz.io` health 200; publish/read-back verified. Old `nfty` hostname is a 502 typo, not the server. |
| R-18 | **Room v2 is forward-only:** a v1 build cannot open a v2 database; downgrade would fail. | Medium | Accepted by design (no destructive fallback). Add v3 migrations for fixes; never downgrade on a user device. Test `Migration1To2Test`. |
| R-19 | **Mock executor could be mistaken for real execution.** | Medium | Mitigated: mandatory “MOCK — no real execution” banner, `mock: true` results, no network/shell code. Real gateway is Phase 6. |
| R-20 | **Command replay/expiry bypass if a future executor trusts the app blindly.** | High (Phase 6) | Envelope requires single-use nonce, ±60 s issue window, 5 min expiry, allowlist, explicit confirmation. The gateway must re-validate independently. |
| R-21 | **Activity/outbox growth on a low-RAM device.** | Medium | Bounded queries and retention: activity 90 days/5,000 rows per profile; outbox 30 days/500 terminal entries; never prune queued work. Measure on hardware. |
| R-22 | **No automatic publish retry:** a failed publish waits for the user. | Low | Accepted Phase 4 default; automatic retry needs an idempotency design and is deferred. |
| R-23 | **Stage A migration is verified on Robolectric only**, not on a physical device with a real v1 database. | Medium | `Migration1To2Test` builds the v1 schema from `1.json` and opens v2 through Room; still add an instrumented/device migration test when hardware exists. |
| R-24 | **Schema v2 is committed before the repositories and UI exist.** A later entity change would need a v3 migration even if no user ever received v2. | Low | Accepted: v2 is additive and stable; fix forward with explicit migrations, never edit v2 or use destructive fallback. |
| R-25 | **Substring search uses SQL `LIKE`, not FTS.** Large histories may scan. | Medium | Bounded by `LIMIT`/`OFFSET`; measure on a low-end device; FTS is a future optimization, not a Stage A requirement. |
| R-26 | **Outbox/activity/command pruning could remove old terminal history** by design. | Low | Retention is 90 days/5,000 activity rows and 30 days/500 terminal outbox rows; queued work is never pruned. Documented in Settings in Stage D. |

| R-27 | **Live ntfy is unauthenticated.** Anyone with a topic name can read or publish. | High | Open owner decision. Enable auth only after updating PVE webhook, watchtower and docker-events; do not default-deny blindly. |
| R-28 | **Gateway is not deployed.** Real Proxmox views/commands are unavailable from the app. | Medium | Documented; least-privilege token ready. Deploy on an existing private host with TLS. |
| R-29 | **Release is test-only signed.** Not a production/Play Store identity. | Medium | Accepted for owner evaluation; generate a production key before distribution. |
| R-30 | **LAN HTTP origin is unusable by the app.** `http://<private-ntfy-lan>:8201` is CLI-only because the app enforces HTTPS and Android denies cleartext. | Low | Use the public HTTPS URL; do not weaken TLS for LAN convenience. |
| R-31 | **Old `nfty` hostname returns 502.** Cached configs may still point at it. | Medium | Use `ntfy.rplab.qzz.io`; update any remaining client configs. |
| R-32 | **Backup target is a single aging HDD** (29,108 h; 11,773 reported uncorrectable events; no redundancy). | **High** | Do not rely on it as the only copy. Add a healthy second target and off-site copy; run restore drills. |
| R-33 | **Backups for CT104 and VM200 are stale/failing** because the 150 GiB target is ~83 % full. | **High** | Diagnosed; do not delete the old archives until a replacement target is verified. See `BACKUP_BASELINE_PLAN.md`. |
| R-34 | **No verified off-site backup exists.** | **High** | A local second disk is not disaster recovery. Add an external destination and test a restore. |
| R-35 | **ntfy is public and unauthenticated**; anyone with a topic name can read or publish. | **High** | Enable auth/ACLs only after every producer is migrated; never enable default-deny blindly. |
| R-36 | **PVE email fallback is broken** (Postfix bounces to an old Gmail address). | Medium | Fix the recipient or remove the fallback; do not rely on email as the only alert path. |
| R-37 | **CT104 backup excludes bind-mounted application data** (`backup=0` on downloads, backup, Immich drive, annotations). | **High** | Document the gap; back up those datasets separately; do not call the CT104 archive a full application backup. |
| R-38 | **Natural-language control plane could be mistaken for arbitrary command execution.** | **High** (Freebuff) | Only typed, allowlisted operations; no shell/SQL/URL; preview, step-up, audit. |
| R-39 | **Gateway write scope could be widened too early.** | **High** (Freebuff) | Keep the current PVE token read-only until each typed mutation is implemented and tested; separate write token with least privilege. |
| R-40 | **Old exposed passwords remain compromised.** | **High** | Rotate PVE root, Mint sudo and GitHub PAT; never reuse them in the redesign. |

## Freebuff Workstream B — Phase 0 additions (2026-11-12)

| ID | Risk | Severity | State / mitigation |
|---|---|---|---|
| R-41 | **ntfy ACLs are per user, not per token.** `kaminari_app` cannot have different Android and gateway permissions; splitting them under one user is impossible. | **Critical** | Open. Decision P-02 required before any ACL is applied. Do not assume token-level isolation. |
| R-42 | **Gateway approval is a nonce echo.** Possession of the shared bearer token is enough to mutate; no second factor or independent approval exists. | **Critical** | Open. Mutations must stay disabled; implement real step-up (P-06) before use. |
| R-43 | **Gateway replay protection and audit are volatile.** A process restart clears the replay cache; audit is a log line, not durable. | **High** | Open. Persist idempotency and an append-only audit store (P-05). |
| R-44 | **Namespace conflict in older documents.** Discovery docs and the priority standard still say `rplab-*`; following them creates topics no producer uses. | **High** | Open. Approved set is `kf-`; reconcile documents (P-01). |
| R-45 | **Android and gateway command catalogues do not match**, and the app reply topic is `rplab-command-results`. | **High** | Open. Reconciling contracts is Phase 1; real execution remains impossible until fixed. |
| R-46 | **The app seeds no topics.** A fresh install receives nothing until the user adds a topic; sample names are not live. | **Medium** | Open. Migration must seed the approved catalogue explicitly. |
| R-47 | **No web frontend exists.** A web control plane cannot reuse any current UI or API contract. | **Medium** | Accepted as scope; do not start it before the gateway identity and read catalogue are stable. |
| R-48 | **Admin-capable `NtfyAdminClient` ships in the APK** (unwired, accepts an admin credential at runtime). | **High** | Open. Remove or release-gate per P-03. |
| R-49 | **Gateway has untyped raw PVE passthrough routes** (`/v1/proxmox/*`) outside the typed catalogue, risk model and audit. | **High** | Open. Every route must go through the typed layer in Phase 2. |
| R-50 | **Accessing ntfy via the live server shows auth disabled and no persistent cache.** A restart loses cached messages; the app must keep Room as the source of truth. | **High** | Confirmed live 2026-11-12. Do not treat ntfy as durable history or an audit store. |
| R-51 | **Reserved/dormant topics can drift into partial use.** `kf-iot` has no producer; `kf-stickers-*` has no pipeline. | **Medium** | Open. Create them denied or defer; never claim implementation without a real publisher. |
| R-52 | **Live migration can silently break all alerts.** Every current producer is anonymous and `auth-default-access=deny-all` would cut them off. | **Critical** | Mitigated only by the staged migration in `TOPIC_MIGRATION_MATRIX.md`; requires owner approval and rollback evidence. |

## Backup Workstream A — Phase A additions (2026-11-12)

| ID | Risk | Severity | State / mitigation |
|---|---|---|---|
| BR-01 | Three SSD generations exceed the 20 % reserve (199.8 GiB on ~235 GiB usable). | **High** | Open; approve 2 generations or a documented 3-generation exception with growth cap. |
| BR-02 | 150 GiB HDD quota cannot stage two old + one new (199.8 GiB). | **High** | Open; raise to 220–240 GiB or accept transient one-generation copy. |
| BR-03 | HDD shares the physical pool with downloads, Immich and a VM disk. | **Critical** | Open; secondary only; off-site required. |
| BR-04 | HDD has 29,127 h and 11,773 reported uncorrectable events. | **High** | Open; replace/demote; SMART/scrub monitoring; verify every copy. |
| BR-05 | No verified off-site backup. | **Critical** | Open; encrypted off-site target and restore test required. |
| BR-06 | Existing archives have no checksums or restore proof. | **High** | Open; SHA-256, format test, manifest, restore drill. |
| BR-07 | CT104 archive excludes four bind-mounted datasets. | **Critical** | Open; separate dataset/application backup. |
| BR-08 | Immich/Docker databases are crash-consistent only. | **High** | Open; application-aware dumps/quiesce. |
| BR-10 | SSD carve-out consumes the empty `fast-data` pool. | **Medium** | Open; approve LV size and future-guest trade-off. |
| BR-11 | SSD namespace utilization is 192 GB despite 0 % thin-pool data. | **Medium** | Open; investigate/benchmark before relying on full capacity. |
| BR-14 | PVE `prune-backups` and a custom script could both delete. | **High** | Open; disable/neutralize PVE storage pruning before automation. |
| BR-16 | The PVE notification 400 fix has not been exercised by a real post-fix backup. | **High** | Open; confirm a real job reaches ntfy. |
| BR-19 | Restore could overwrite a running guest if a VMID is reused. | **High** | Open; restore to new VMID/isolated bridge; explicit confirmation. |

## Freebuff Workstream B — Phase 1 additions (2026-11-12)

| ID | Risk | Severity | State / mitigation |
|---|---|---|---|
| R-53 | **Contract drift** between the JSON contract and Kotlin/Python constants | High | Mitigated in Phase 1: both runtimes load or test against `contracts/kf_contract.v1.json`. Any future constant must update the contract and parity tests. |
| R-54 | **P-02 is only modelled, not live.** No `kf_gateway` ntfy identity/token/ACL exists. | Critical | Open until Phase 5 creates and verifies them; do not claim the split is enforced on the server. |
| R-55 | **Gateway approval remains nonce echo**, now explicitly `is_step_up = False`. | Critical | Open; mutations stay disabled by default. P-06 required before enabling any mutation. |
| R-56 | **Audit and idempotency are in memory** (`durable = False`). | High | Open; not restart-safe or a system of record. P-05 required before trusting mutations. |
| R-57 | **Admin boundary has no production implementation.** `UnavailableGatewayAdminService` is the default and every capability is planned/gateway-required. | Medium | Accepted for Phase 1; the UI cannot fake execution. Implement typed operations in Phase 2. |
| R-58 | **Raw PVE read routes still exist** and are only identity-read-checked. | High | Open; Phase 2 must remove them or route every call through the typed catalogue. |
| R-59 | **Room v3 migration is verified on Robolectric only**, not on a device with a real v2 database. | Medium | Open; add a device upgrade test before release. |
| R-60 | **Legacy reply topic remains accepted** for correlation. | Low | Accepted deliberately for staged migration; remove only after all producers use `kf-command-result`. |
| R-61 | **No topic seeding in the app**: the catalogue is modelled but a fresh install still receives nothing. | Medium | Open; seed without changing production topics in a later approved phase. |
