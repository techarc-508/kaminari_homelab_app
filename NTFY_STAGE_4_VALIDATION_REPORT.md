# Kaminari Nfty — Stage 4 Validation Report

**Stage:** Phase 8, Stage 4.
**Start commit:** `381235c`
**Commit at report time:** pending finalization below.
**Date:** 2026-11-10

## 1. Objective

Complete Stage 4 by discovering existing ntfy infrastructure, producing a topic
reuse plan, auditing app integration, running verified build/test/lint checks,
delivering the verified APK to the VM Desktop, and documenting everything.

## 2. Infrastructure discovery summary

### 2.1 What was found
- A ntfy process `ntfy serve` is running on the Proxmox host `pve`
  (<private-pve-host>).
- The repository references the public URL `https://nfty.rplab.qzz.io` as the
  default ntfy endpoint.
- DNS for `nfty.rplab.qzz.io` resolves to Cloudflare addresses
  `104.21.63.209` and `172.67.150.94`.

### 2.2 What was not reachable
- `https://nfty.rplab.qzz.io` returned HTTP 502 from the Proxmox host at audit
  time.
- Because of that, live topic list, live auth model, and live admin endpoint
  behavior could not be confirmed from this environment.

### 2.3 What is plausible but unverified
- The Proxmox-host ntfy and the Cloudflare URL may be the same endpoint, but
  that could not be confirmed from this audit.
- The existing topic set described in the repository gives a strong hint of the
  intended topic layout, but topics were not confirmed live.

See `NTFY_EXISTING_INFRASTRUCTURE_AUDIT.md` for full evidence.

## 3. Topic reuse plan summary

### 3.1 Reuse approach
- Prefer existing topics where purpose and ACLs allow.
- Keep command topics separate from general inbox/alert topics.
- Do not repurpose or change existing production topics in this stage.
- Create new topics only if needed and only with owner approval.

### 3.2 Proposed reuse candidates from repository evidence
- `homelab-alerts`
- `iot-telemetry`
- `backups`
- `build-server`
- `security`

These are repository-described intended topics, not confirmed live topics yet.

See `NTFY_TOPIC_REUSE_AND_INTEGRATION_PLAN.md`.

## 4. App integration audit summary

### 4.1 Findings
- Server URL is configurable and defaults to the existing production URL.
- Auth model supports guest and authenticated use.
- Delivery uses SSE plus a Battery Saver WorkManager catch-up path.
- Outbox publish/result handling is in place.
- Command Centre defaults to the mock executor.
- Gateway auth is separate from ntfy auth.
- Admin client is intentionally limited.
- Secret handling/redaction is in place.
- Error handling covers DNS, timeout, HTTP, auth/PERMISSION_DENIED-like states,
  and stream drops.

### 4.2 Blockers
- The current integration blocker is infrastructure verification, not app
  architecture.
- Live ntfy behavior cannot be fully validated until the reachable production URL
  and live topic/auth model are confirmed.

See `NTFY_APP_INTEGRATION_AUDIT.md`.

## 5. APK delivery summary

- Desktop path:
  `/home/pushpal/Desktop/KaminariNfty/KaminariNfty-latest-verified.apk`
- Source commit: `381235c`
- Build variant: debug
- Signing: debug-signed only, not production-signed
- Size: 19918197 bytes
- SHA-256: `a3d29f0557b0f247e6246439d1346bc2396f26c9c8a8178f6f955f2dea3b17ac`
- Source and Desktop SHA-256 match: yes

See `APK_DESKTOP_DELIVERY_REPORT.md`.

## 6. Verification results

- Android tests: `tests=361 failures=0 errors=0 skipped=2`
- Gateway tests: 9 passed, 1 warning
- Debug build: successful
- Lint: successful, 0 errors

## 7. Carry-forward blockers

### 7.1 Stage 4 unresolved items
- Live ntfy reachability and correctness of `https://nfty.rplab.qzz.io` from the
  intended client path.
- Live topic inventory and ACL verification.
- Live auth/guest/superuser behavior.
- Live admin endpoint verification.
- Confirmation whether Proxmox-host ntfy and the Cloudflare URL are the same
  endpoint.

### 7.2 Previously tracked release blockers
- R8/minification review
- Backup/data extraction rules finalization
- Network security finalization
- Production signing key
- Physical Android and E-Ink/Boox validation
- Live Proxmox validation and ACL provisioning

These remain tracked and were not removed during this stage.

## 8. Commit

Commit once the remaining documentation updates are in place.

## 9. Remaining owner actions

1. Confirm the correct reachable production ntfy URL.
2. Confirm whether Cloudflare URL and Proxmox-host ntfy are the same endpoint.
3. Verify live topics and ACLs.
4. Verify guest and authenticated access.
5. Verify admin endpoint support if admin features are needed.
6. Approve any new topic creation or permission changes.
7. Perform physical/Boox validation before release.
8. Generate and back up a production signing key before release.
