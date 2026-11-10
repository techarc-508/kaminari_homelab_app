# Kaminari Nfty — Topic Reuse and Integration Plan

**Stage:** Phase 8, Stage 4B.
**Start commit:** `381235c`
**Based on:** NTFY_EXISTING_INFRASTRUCTURE_AUDIT.md
**Date:** 2026-11-10

## 1. Goal

Reuse the existing ntfy deployment as much as possible and minimize new topic
creation. Only create new topics if an existing topic is unsuitable and the need
is unavoidable.

Current constraint: live topic enumeration was not possible from this audit
because `https://nfty.rplab.qzz.io` returned 502 from the audit host at audit
time. The plan is therefore based on repository-described topics and the
discovered ntfy instance, with live verification listed as required owner action.

## 2. Existing topic candidates from repository evidence

| Topic | Repository evidence | Proposed reuse |
|-------|--------------------|----------------|
| `homelab-alerts` | SampleDataProvider sample actions | Reuse for PVE/homelab alerts if ACLs allow |
| `iot-telemetry` | SampleDataProvider sample actions | Reuse if app telemetry/device topics fit |
| `backups` | SampleDataProvider sample actions | Reuse for app/backup status if ACLs allow |
| `build-server` | SampleDataProvider sample actions | Reuse for CI/gateway/build events if ACLs allow |
| `security` | SampleDataProvider sample actions | Reuse for security-related notices if ACLs allow |

These are treated as **existing-intended topics**, not yet confirmed live topics.

## 3. Mapping Kaminari Nfty features to reuse

### 3.1 Incoming notifications / Inbox
- Use one or more profile-specific topics, likely existing ones if the user
  already has the right publish/subscribe access.
- If the existing topics are already used by other apps, do not collide. Prefer
  dedicated Kaminari Nfty topics for app inbox where reuse would risk mixing
  unrelated notifications.

### 3.2 Guest notifications
- Guest notifications must go to topics the guest auth mode can actually publish
  to and subscribe to.
- This depends on the live ntfy auth/ACL model, not on the app code alone.
- If guest access is disabled or restricted on the existing server, guest topics
  may need the owner to confirm ACLs.

### 3.3 Superuser notifications
- Use topics restricted to authenticated/superuser access if supported.
- Do not assume guest visibility for superuser-only topics.

### 3.4 Homelab/PVE alerts
- Strong reuse candidate: existing `homelab-alerts` if it already serves that
  purpose and ACLs are appropriate.
- If PVE already publishes there, Kaminari Nfty can subscribe to it without
  changing PVE configuration.
- Do not change PVE notification config in this stage.

### 3.5 Application and service alerts
- Reuse existing alert topics where purpose and access match.
- Otherwise create small, clearly named dedicated topics.

### 3.6 Command requests and responses
- Use dedicated request/response topics, not general alert topics.
- Do not reuse public notification topics for command traffic.
- Command topics should be scoped to Kaminari Nfty command flow and access
  controlled accordingly.

### 3.7 Command execution reports
- Use a distinct topic or inbox from command requests.
- Keep command reporting separate from general app inbox where practical.

### 3.8 Delivery and error reporting
- Internal app-level delivery/error reporting is primarily an app concern.
- If error notices should reach a shared inbox, publish to an appropriate
  existing alert topic only if ACLs allow and it does not pollute unrelated
  consumers.

### 3.9 Administrative notifications
- Use a separate admin topic if the app needs admin-only notices.
- Prefer existing admin topics only if the owner confirms the access model.

## 4. Reuse decision rules

- Reuse an existing topic only if:
  - its current purpose matches the Kaminari Nfty feature, and
  - the app can publish/subscribe without changing unrelated consumers, and
  - the owner confirms ACLs are acceptable.
- Do not reuse a topic just because the name is convenient.
- Do not repurpose an existing topic without owner approval.
- If reuse is unclear, create a dedicated topic and document the reason.

## 5. Topics that should stay untouched here

- Production topics not used by Kaminari Nfty.
- Topics owned by other services with no shared purpose.
- PVE notification configuration.
- Existing user/role/token configuration.

## 6. If new topics are needed

If live verification shows existing topics are unsuitable, create the smallest
new topic set needed and only with owner approval. Proposed new topic namespaces:

- `kaminari-nfty/inbox-<profile>` for profile inbox if no existing inbox topic
  fits.
- `kaminari-nfty/commands/request` for command requests.
- `kaminari-nfty/commands/response` for command results.
- `kaminari-nfty/admin` for admin notices if needed.

These are proposals, not created topics.

## 7. Required owner decisions

1. Confirm the current correct production ntfy URL.
   - Likely `https://nfty.rplab.qzz.io`, but currently 502 from pve.
2. Confirm whether the Cloudflare-proxied URL and the Proxmox-host ntfy are the
   same endpoint, or whether there is a separate internal ntfy URL.
3. Confirm live topic list and ACLs.
4. Confirm guest vs authenticated access for the topics Kaminari Nfty will use.
5. Confirm any existing PVE notification integration and whether Kaminari Nfty
   should subscribe to it.
6. Approve any new topic creation.
7. Approve any permission changes, if needed later.

## 8. Recommended integration order

1. Fix or clarify the reachable ntfy URL first.
2. Verify live topic list and access.
3. Map Kaminari Nfty inbox and command topics.
4. Validate subscription and publish in a controlled manner.
5. Enable command flow only after read/health and basic publish/subscribe work.
6. Keep mutations and command execution gated behind their own approval and
   least-privilege model.
