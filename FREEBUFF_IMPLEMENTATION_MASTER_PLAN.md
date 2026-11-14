# Freebuff Implementation Master Plan

**Handover from OpenCode Stage 1**
**Date:** 2026-11-12
**Authority:** Owner-approved Kaminari Fuji Homelab redesign. Freebuff must not reinterpret or reduce this scope.

## 1. Locked decisions

- Namespace: **`kf-`**.
- **22 topics**, no `kf-builds`.
- Legacy topics preserved during migration; no deletion without separate owner approval.
- Identities: `kfadmin`, `kaminari_app`, `kf_monitor`, `kf_music`, `kf_stickers`, `kf_news`.
- `kfadmin` = ntfy superadministrator only.
- `kaminari_app` = gateway/application owner, **not** an ntfy administrator.
- Android and gateway use separate tokens; no admin token in the APK.
- Level 4 administration is approved, including destructive operations with preview, exact target, step-up authentication and audit.
- No arbitrary shell execution; all PVE operations through the typed gateway.
- No new LXC/CT/VM/ntfy instance.
- Instant and Battery Saver remain the Android delivery modes; E-Ink remains a complete second profile.

## 2. Approved topic catalogue

```
kf-web
kf-pve-host
kf-pve-ct
kf-pve-vm
kf-storage
kf-backups
kf-network
kf-services
kf-security
kf-ntfy
kf-digest
kf-music-pipeline
kf-music-reports
kf-stickers-pipeline
kf-stickers-reports
kf-news
kf-news-reports
kf-command-request
kf-command-result
kf-admin-audit
kf-diary
kf-iot
```

`kf-builds` is **excluded**.

Legacy: `KAMINARI-FUJI-Homelab`, `kaminari-diary`, `kaminari-onboarding-test` remain until every producer/subscriber is migrated and the owner approves retirement.

## 3. Stage 1 state passed to Freebuff

| Item | State |
|---|---|
| Archive inventory | 12 archives, 124.802 GiB, exact manifest in `OLD_BACKUP_DELETION_MANIFEST.md` |
| Archive deletion | **Not executed** — awaiting exact owner confirmation |
| Storage health | Single 500 GB HDD, 29,108 h, 11,773 reported uncorrectable events; ZFS ONLINE; **not a trustworthy sole target** |
| CT104 backup | Failing since 2026-10-20 due to target capacity |
| VM200 backup | Failing 2026-11-11/12 due to target capacity/broken pipe |
| PVE notification | **Repaired** — body reduced to `{{ title }}`; PVE test to onboarding accepted; production URL restored |
| Baseline | **Partial**: CT100–CT103 2026-11-12 OK; CT104/VM200 missing |
| New baseline | 2026-11-12; blocked until storage option approved |
| Off-site | **Missing** |

## 4. Implementation order

### Stage 2 — Protect and repair (must precede auth migration)

1. Owner selects Option A/B/C in `BACKUP_BASELINE_PLAN.md`.
2. If Option A: add the existing NVMe as a directory backup target; no new CT/VM.
3. Delete old archives only after exact manifest confirmation and target verification.
4. Run a complete six-guest baseline; verify each archive.
5. Confirm a backup event reaches `kf-backups` after migration; until then keep the repaired PVE target.
6. Label any single-device baseline honestly; add off-site as an open blocker.

### Stage 3 — Identity and ACLs

1. Back up the ntfy auth database and `server.yml` before changes.
2. Create `kfadmin` (admin, random password, private store).
3. Create `kaminari_app` (normal user, no admin).
4. Create `kf_monitor`, `kf_music`, `kf_stickers`, `kf_news`.
5. Create the 22 topics.
6. Apply the ACL matrix in `KF_TOPIC_ACL_MATRIX.md`.
7. Issue distinct tokens; store only in private stores.
8. Test every allow and deny path; test wildcard precedence.
9. Migrate producers one domain at a time.
10. Only after all producers are migrated: `auth-default-access=deny-all`.
11. Remove anonymous access after verification.
12. Do not delete legacy topics in this stage.

### Stage 4 — Monitoring

1. Website/TLS/DNS/tunnel checks → `kf-web`.
2. PVE node/guest/storage/backup monitors → `kf-pve-*`, `kf-storage`, `kf-backups`.
3. Docker/service checks → `kf-services`.
4. Network/security/ntfy checks → `kf-network`, `kf-security`, `kf-ntfy`.
5. Daily digest → `kf-digest`.
6. Use existing Uptime Kuma, Healthchecks, Scrutiny, Beszel, Docker, PVE; do not install a duplicate stack.
7. Dedupe, cooldown, hysteresis, recovery messages per `NOTIFICATION_PRIORITY_AND_FORMAT_STANDARD.md`.

### Stage 5 — Gateway and Level 4 control plane

1. Deploy the FastAPI gateway on an approved existing host with HTTPS.
2. Implement OIDC/MFA, short-lived sessions, refresh rotation, device revocation, rate limits, audit.
3. Implement read-only PVE operations first with the existing `kaminari-gateway@pve`/`gateway-ro` token.
4. Add reversible operations.
5. Add destructive operations with preview, exact target, step-up and audit.
6. Do not widen PVE privileges until each operation is tested.
7. Do not expose raw PVE or SSH.

### Stage 6 — Android

1. Update the app to the five destinations and the 22-topic model where applicable.
2. Add News cards, Music reports, Stickers reserved screens, Command Centre and Activity.
3. Keep Instant/Battery Saver/E-Ink profiles.
4. Command Centre uses the gateway API, never an ntfy command listener.
5. No PVE credentials in the APK.
6. Test on a real device; no hardware claim without evidence.

## 5. Required specifications

Freebuff must implement from:

- `APPROVED_KF_TOPIC_CATALOGUE.md`
- `KF_TOPIC_ACL_MATRIX.md`
- `KF_ADMIN_AND_GATEWAY_AUTHORIZATION_MODEL.md`
- `KAMINARI_LEVEL4_CONTROL_PLANE_SPEC.md`
- `MUSIC_PIPELINE_NOTIFICATION_CONTRACT.md`
- `STICKERS_PIPELINE_NOTIFICATION_CONTRACT.md`
- `KF_NEWS_PIPELINE_NOTIFICATION_CONTRACT.md`
- `NOTIFICATION_PRIORITY_AND_FORMAT_STANDARD.md`
- `MONITORING_OPPORTUNITY_MATRIX.md`

## 6. Security requirements

- No secret in Git, reports, chat, logs or APK.
- No PVE root credential anywhere in the app/gateway.
- No arbitrary shell/SQL/URL execution.
- No admin token in Android.
- No anonymous ntfy write after migration.
- Back up before account/ACL changes; rollback documented.
- Test allow and deny paths for every identity.
- Do not use previously exposed passwords.

## 7. Acceptance criteria

- All 22 topics exist and are ACL-protected.
- `kfadmin` is the only ntfy admin.
- `kaminari_app` cannot administer ntfy but can read the ecosystem and issue commands.
- Android and gateway tokens are separate and revocable.
- Every producer can write only its topics.
- Anonymous access is denied after migration and all producers still work.
- PVE backup baseline is complete and verified, or the storage blocker is explicitly accepted.
- PVE notification sends a short structured message to `kf-backups` after migration.
- Gateway read-only operations work; every mutation is typed, previewed, confirmed, authorized and audited.
- No destructive operation is reachable without step-up and exact-target confirmation.
- Android builds, tests and lint pass; device testing is reported honestly.
- No secret is exposed.

## 8. Blockers Freebuff must not ignore

1. **Backup capacity/health** — no trustworthy second target; old archives are the only recovery points for CT104/VM200.
2. **No off-site backup**.
3. **NTFY auth is currently disabled** — enabling it without migrating every producer will break production alerts.
4. **Legacy topics are still in use** — do not delete them.
5. **No physical Android device** on the development VM; do not claim hardware validation.
6. **No gateway deployment host** chosen; the Mint VM is a candidate but not always-on.
