# Decision Log — Kaminari Nfty V1

Append-only record of owner decisions and the technical choices that follow from them.

## Owner-locked

| ID | Decision | Source |
|---|---|---|
| D-01 | Delivery supports **both Instant and Battery Saver**; do not silently choose one and never call Battery Saver instant. | `AGENTS.md` owner-locked decisions |
| D-02 | Admin scope includes ntfy administration and PVE/homelab operations; PVE goes through a separately deployed trusted gateway, never credentials or arbitrary shell in the APK. | `AGENTS.md` |
| D-03 | UI provides two complete profiles: Standard Kaminari and dedicated E-Ink (not a grayscale tint). | `AGENTS.md` |
| D-04 | Onyx Boox model is unknown until the device arrives; keep E-Ink generic, no invented vendor APIs. | `AGENTS.md` |
| D-05 | Notification actions beyond "open message" (mark read/save) are deferred; not required for Phase 3. | Owner review, 2026-11-09 |
| D-06 | The `NftyTopBar` live-clock fix belongs in Phase 3, with regression evidence. | Owner review, 2026-11-09 |
| D-07 | Instant is best-effort live delivery subject to Android/OEM restrictions; Battery Saver is inexact periodic catch-up. No guarantee of instant delivery. | Owner review, 2026-11-09 |
| D-08 | Phase 4 is **not authorised** until explicit owner approval. | Owner checkpoint instruction |
| D-09 | Phase 3 is **ACCEPTED — PASS WITH CONDITIONS** (2026-11-09). The original FAIL and remediation history are preserved. No hardware validation may be claimed. | Owner instruction, 2026-11-09; `PHASE_3_ACCEPTANCE.md` |
| D-10 | The repository-root `PLAN.md` is authoritative; the plan-folder copy is a mirror. Phase 1/2 reports are mirrored into the repository for a self-contained record. | Owner instruction, 2026-11-09 |
| D-11 | Phase 4 default scope: publish with URL/metadata attachments (binary upload deferred), durable outbox, activity timeline, typed command envelope with **mock** executor, Room-backed inbox/topic UX, multiple profiles with safe switching and a migration/URL-change preview. Approved as the Phase 4 defaults; Stage A authorized by D-12. | Owner instruction, 2026-11-09 |
| D-12 | **Phase 4 authorised (2026-11-09) for Stage A only.** Owner locked the approved scope: five destinations, URL/metadata attachments only, durable outbox with manual retry, Room activity timeline, typed `rplab.command.v1` envelope with **mock executor only**, profile isolation and URL-change preview. Stages B–F require owner approval at the Stage A gate. | Owner authorization, 2026-11-09 |

## Technical decisions (Phase 4 Stage A, 2026-11-09)

| ID | Decision | Rationale |
|---|---|---|
| T4A-01 | Room v2 adds only `outbox_entries`, `activity_events` and `command_requests`; existing tables and columns are untouched. | Additive migration preserves all existing messages, saved state, subscriptions and profiles. |
| T4A-02 | `MIGRATION_1_2` is explicit and `fallbackToDestructiveMigration` is absent. | AGENTS.md forbids destructive migration in release builds; user history must survive upgrades. |
| T4A-03 | Outbox transitions are conditional SQL updates (`WHERE state = :expected`), not read-modify-write. | Prevents double sends/claims from concurrent senders and makes retry idempotent at the database boundary. |
| T4A-04 | Retention: activity 90 days / 5,000 rows per profile; outbox 30 days / 500 terminal rows per profile. Queued work is never pruned. | Bounded storage for low-RAM devices; audit trail survives normal use. |
| T4A-05 | Migration test constructs the v1 database from the exported `1.json` schema, not hand-written v1 DDL. | Proves the actual shipped schema can migrate, not a convenient approximation. |
| T4A-06 | Repository classes are deferred to Stage C; Stage A tests the DAOs and state machine directly. | Keeps Stage A focused on persistence and avoids duplicate repository scaffolding before publish/activity behavior exists. |

## Technical decisions (Phase 3 remediation, 2026-11-09)

| ID | Decision | Rationale |
|---|---|---|
| T3-01 | Only `INSTANT` and `BATTERY_SAVER` are selectable delivery modes; legacy persisted `SAVED` migrates to `BATTERY_SAVER`. | Owner-locked two-mode requirement; migration preserves delivery rather than silently removing it. Saved *messages* are unrelated. |
| T3-02 | Fresh installs default to `BATTERY_SAVER`. | Least-surprise: background catch-up works without the user finding Settings, without a permanent foreground notification. |
| T3-03 | A process-wide `DeliveryStatusStore` is the source of Instant-mode connection state; the service is the only stream owner. | Fixes the false "offline" UI without opening a duplicate subscription. |
| T3-04 | On Android 15+ `dataSync` timeout, record `TIMED_OUT`, stop the service within the deadline, persist Battery Saver and schedule catch-up. | Prevents the fatal `RemoteServiceException`; the budget is a platform limit and is not extended. |
| T3-05 | Terminal auth/forbidden/retry-exhaustion stops the foreground service and posts the real reason. | A dead stream must never keep a "live delivery" notification. |
| T3-06 | Notification deep links resolve by message ID from Room after the profile loads; the in-memory window is only a cache. | Cold starts begin with an empty window; Room is the source of truth. |
| T3-07 | Keep `BOOT_COMPLETED` only (no `LOCKED_BOOT_COMPLETED`); use the app-owned scope and a tested `BootDeliveryRestorer`. | Credential-encrypted DataStore is unavailable before first unlock; the unmanaged scope violated the architecture rules. |
| T3-08 | Test-only `androidx.work:work-testing:2.10.1` is allowed. | Constructs the real worker; matches production WorkManager; not packaged in the APK. |

## Open decisions

1. Notification actions remain deferred by D-05; revisit only if the owner reopens it.
2. Whether to pursue an FCM/UnifiedPush fallback (optional in PLAN).
3. E-Ink profile specifics once the Boox model/firmware is known.
4. Phase 4: Stage B–F approval after the Stage A report; JSON-root publish,
   `view`-only actions, manual-only outbox retry, mock-only command centre and
   the profile URL-change preview remain as approved defaults
   (`PHASE_4_ARCHITECTURE.md` §16).
5. Resolved: Saved remains an Inbox filter (`D-11`).
6. Phase 6: real allowlisted PVE actions and gateway trust boundary.
7. Stage 4: whether `https://nfty.rplab.qzz.io` is still the correct reachable
   production ntfy URL, and whether the Proxmox-host ntfy and the Cloudflare URL
   are the same endpoint; owner to confirm after the origin is healthy.
8. Stage 4: which existing topics Kaminari Nfty may reuse for inbox, commands,
   homelab alerts and admin notices; owner to approve reuse or new topic creation.

## Documentation reconciliation (2026-11-09)

- Two divergent `PLAN.md` copies existed. The repository-root copy is canonical;
  the plan-folder copy is now a mirror (cycle 2).
- `PHASE_1_REPORT.md`, `PHASE_2_REPORT.md` and `PHASE_2_GATE_REPORT.md` were
  mirrored into the repository root; no duplicate copies are maintained in the
  root.
- The handoff's phase-scope wording conflated Phase 4 with E-Ink and
  administration. `PLAN.md` is authoritative: E-Ink is Phase 5, ntfy admin and
  the PVE gateway are Phase 6.

## Final infrastructure decisions (2026-11-11)

| ID | Decision | Reason |
|---|---|---|
| D-12 | Canonical ntfy URL is `https://ntfy.rplab.qzz.io`; `nfty` is obsolete and returns 502. | Live DNS/TLS/health evidence from Mint and PVE. |
| D-13 | Use Guest/Anonymous in the app while ntfy auth is disabled; do not create an admin token. | `users=0`, `enable_login=false`; no token exists; app must not hold admin. |
| D-14 | PVE gateway identity is `<gateway-user>@pve!<token-id>` with privilege separation and `KaminariRead`. | Least privilege; verified read-only API. No mutation privilege. |
| D-15 | Release APK is signed with a test-only key, not a production key. | Owner instruction; production key remains a separate decision. |
| D-16 | Do not deploy a new CT/VM or expose the gateway publicly. | Owner boundary; no always-on private host chosen yet. |
| D-17 | Keep `KAMINARI-FUJI-Homelab` read-only; use `kaminari-onboarding-test` for tests. | Production alerts must not be polluted. |

## Ntfy redesign decisions (2026-11-12)

| ID | Decision | Reason |
|---|---|---|
| D-18 | All new notification topics use the `kf-` prefix; **22 approved topics** and **no `kf-builds`**. | Owner-approved redesign; catalogue is locked in `APPROVED_KF_TOPIC_CATALOGUE.md`. |
| D-19 | `kfadmin` is the only ntfy administrator; `kaminari_app` is a normal ntfy user and the gateway/application owner. | Separates ntfy administration from application authority; no admin token in Android. |
| D-20 | The gateway uses a separate token under `kaminari_app`; Android and gateway tokens are distinct and revocable. | Token isolation and independent revocation. |
| D-21 | Preserve `KAMINARI-FUJI-Homelab`, `kaminari-diary` and `kaminari-onboarding-test` until every producer/subscriber is migrated. | No silent deletion of production history. |
| D-22 | Stage 1 does **not** delete the old backup archives; deletion requires exact owner confirmation of the manifest. | The old archives are the only recent recovery points for CT104/VM200 and the HDD is at-risk. |
| D-23 | Level 4 administration is approved, including destructive operations, but only through typed gateway operations with preview, exact target, step-up and audit. | Owner-approved full control plane without arbitrary shell or PVE root credentials. |
| D-24 | PVE backup notification body is reduced to `{{ title }}`; the full task log stays in PVE. | ntfy rejects bodies above 4,095 bytes; the old body caused HTTP 400. |

## Freebuff Workstream B — Phase 0 findings (2026-11-12)

These are **findings**, not owner approvals. D-18…D-24 remain the locked decisions.

| ID | Finding | Consequence |
|---|---|---|
| TB0-01 | ntfy ACLs are granted per **user**, not per token. Two tokens under `kaminari_app` inherit identical permissions. | The approved Android/gateway split cannot be implemented as written without a new identity or a combined-ACL decision (P-02). |
| TB0-02 | The live server has authentication disabled and a memory-only message cache. | `kf-admin-audit` cannot be the audit system of record; a durable local store is required (P-05). |
| TB0-03 | The gateway's mutation approval token is the client-supplied nonce. | This is not step-up authentication; mutations must remain disabled until P-06 is implemented. |
| TB0-04 | The Android command catalogue and the gateway catalogue share only two action IDs; the reply topic constant is `rplab-command-results`. | Real command execution cannot work until both are reconciled. |
| TB0-05 | None of the 22 `kf-` topics exists, and no production code references one. | Phase 1 is contract/model work only; topic creation stays in Phase 5. |
| TB0-06 | The Android app seeds **no** topics and its sample topic names are not live. | Migration must explicitly seed the approved catalogue; the app will not receive the legacy stream by default. |
| TB0-07 | No web frontend exists in the repository. | The web console is blocked on the gateway contract, not on frontend work. |
| TB0-08 | The live `nfty.rplab.qzz.io` host still returns 502; `ntfy.rplab.qzz.io` is canonical. | Any stale client config must be corrected before migration. |

### Pending owner decisions (not approved)

| ID | Decision needed |
|---|---|
| P-01 | Confirm `kf-` supersedes `rplab-` in all documents, including the discovery set and priority standard. |
| P-02 | Resolve the Android/gateway token split: combined `kaminari_app` ACLs, a new `kf_gateway` identity, or gateway-owned result/audit topics. |
| P-03 | Decide whether `NtfyAdminClient` may remain in the APK (currently unwired but admin-capable). |
| P-04 | Choose the gateway deployment host and TLS path, or explicitly keep the command centre mock-only. |
| P-05 | Approve a durable local audit store in addition to `kf-admin-audit`. |
| P-06 | Approve a real step-up reauthentication design and retire nonce-echo approval. |
| P-07 | Resolve `kf-diary` write ownership between the ACL matrix and the stickers contract. |
| P-08 | Decide whether `kf-iot` is created denied or deferred until a producer exists. |
| P-09 | Confirm `kaminari_app` access to the legacy test topic for migration testing. |

## Backup Workstream A — Phase A decisions (2026-11-12)

| ID | Decision | Reason |
|---|---|---|
| D-25 | SSD primary is the existing `nvme0n1` **XPG GAMMIX S11 Pro**; HDD secondary is the existing `sda` **ST500DM002**. | Owner-approved architecture; no new hardware or VM/CT. |
| D-26 | The SSD must be used through a new thin LV on the existing `fast-data-ssd` pool, not by formatting or repartitioning the device. | The device is already an active PVE `lvmthin` store. |
| D-27 | The HDD is secondary only; it is not disaster recovery because it is the same physical pool as downloads, Immich and a VM disk. | `zpool`/`zfs` evidence. |
| D-28 | Three SSD generations are **not approved** as a silent default; two are recommended until capacity or reserve is approved. | 3 × 66.6 GiB = 199.8 GiB on ~235 GiB usable; ~15 % free. |
| D-29 | HDD safe copy order requires a 220–240 GiB quota or an explicitly accepted transient one-generation state. | 2 old + new = 199.8 GiB > 150 GiB quota. |
| D-30 | CT104 bind mounts (`backup=0`) and Immich/Docker databases require separate application-aware coverage. | The CT104 archive is rootfs-only. |
| D-31 | Host configuration backups must be encrypted; the key must not be stored on the backup devices. | `/etc/pve/priv` and service credentials are sensitive. |

## Freebuff Workstream B — Phase 1 decisions (2026-11-12)

| ID | Decision | Source / rationale |
|---|---|---|
| D-32 | **P-01 locked:** `kf-` is canonical; exactly **22 topics**; `kf-builds` is excluded. | Owner Phase 1 authorisation. |
| D-33 | **P-02 locked:** a separate non-admin **`kf_gateway`** identity. This supersedes the D-19/D-20 wording that placed the gateway token under `kaminari_app`. | Owner Phase 1 authorisation; ntfy ACLs are per user. |
| D-34 | **P-03 locked:** **Option B** — full Android administration UI backed by the secure gateway. No admin credential or privileged direct execution in the APK. | Owner Phase 1 authorisation. |
| D-35 | `contracts/kf_contract.v1.json` is the single cross-language source of truth; Kotlin and Python must test against it. | Prevents client/gateway drift. |
| D-36 | The wire `requires_confirmation` field is true for every mutation; L1/L2 `ownerConfirmation` is additional UI metadata. | Both runtimes already enforced mutation confirmation; the wire rule is now explicit. |
| D-37 | ntfy `write` does not imply `read`, in both contract implementations. | Matches ntfy write-only grants; avoids encoding an escalation. |
| D-38 | Room v3 adds `category`/`priority` with defaults `OTHER`/`3` and explicit `MIGRATION_2_3`; no destructive fallback. | User history must survive upgrades. |
| D-39 | `kf-command-result` is the canonical command reply topic; `rplab-command-results` is accepted only for staged correlation. | Aligns with P-01 while preserving migration. |
| D-40 | `NtfyAdminClient` moves to test sources as `NtfyAdminFixture`; production `src/main` must contain no admin ntfy call. | P-03 applied without discarding the test harness. |
| D-41 | `UnavailableGatewayAdminService` is the production default; every admin capability is `PLANNED`, `GATEWAY_REQUIRED` or `UNAVAILABLE`. | No fake success and no privileged local execution. |
| D-42 | Gateway audit and idempotency remain in memory in Phase 1 and expose `durable = False`; nonce echo reports `is_step_up = False`. | Honest Phase 1 boundary; P-05/P-06 remain required before mutations. |
| D-43 | Phase 1 does not seed topics, deploy the gateway or change live identities/ACLs/topics. | Phase scope and production-safety rules. |
