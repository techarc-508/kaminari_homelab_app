# PVE Notification Repair Report

**Date:** 2026-11-12
**Scope:** PVE webhook notification target `kaminari-ntfy` only
**Status:** **REPAIRED — PVE test path verified; production backup event pending**

No ntfy authentication, topic migration, ACL, service restart or production alert topic was changed.

## 1. Symptom

The PVE backup notification endpoint returned **HTTP 400** on larger backup logs. CT104 and VM200 backups completed or failed, but the notification did not reach ntfy reliably.

## 2. Exact cause

The webhook body was the full PVE task log. The VM200 task log for 2026-11-12 was **7,556 bytes**; the CT104 log was **1,339 bytes**.

ntfy rejects a request body above **4,095 bytes** with:

```json
{"code":40014,"http":400,"error":"invalid request: attachments not allowed"}
```

Because the webhook method was `post` with the full log as the body, ntfy treated the oversized body as an unauthorized attachment request, not as a message. The CT104 log was under the limit and passed; VM200’s larger log returned 400.

Boundary test against `kaminari-onboarding-test`:

| Body size | Result |
|---:|---|
| 4,095 bytes | `200` accepted |
| 4,096 bytes | `400` `attachments not allowed` |
| 4,097 bytes | `400` `attachments not allowed` |
| 5,000 bytes | `400` `attachments not allowed` |

This is a **body-size** defect, not an ntfy outage and not an authentication failure.

## 3. Configuration inspected

Target: `webhook: kaminari-ntfy` in `/etc/pve/notifications.cfg`.

Original shape:

- Method: `post`
- URL: production topic endpoint
- Body: full PVE notification body including task log
- No custom `Title`, `Tags` or `Markdown` headers

The target URL was restored exactly after testing.

## 4. Repair

Changed only the webhook body template to:

```text
{{ title }}
```

The production URL and method remain unchanged. The notification still carries the PVE title (for example `vzdump backup status (pve): OK`), while the full log stays in the PVE task log rather than being forced into ntfy.

A richer future design should send a short structured summary (`status`, guest, duration, size, error) rather than the full log. That is a Freebuff implementation task; it was not applied in Stage 1.

## 5. Evidence

1. Backup created:

```text
/root/notifications.cfg.backup-20261111T180707Z
```

2. The target URL was temporarily pointed at `kaminari-onboarding-test` for the test only.

3. PVE’s own notification path was invoked:

```text
INFO: notified via target `kaminari-ntfy`
INFO: notified via target `mail-to-root`
```

4. Read-back from the onboarding topic:

```json
{"id":"nftxG2l4pqiS","topic":"kaminari-onboarding-test","title":"KAMINARI-FUJI Homelab - PVE","message":"Test notification"}
```

5. Production URL was restored and the final target was read back:

```json
{"url":"http://<docker-host>:8201/KAMINARI-FUJI-Homelab","method":"post"}
```

The temporary test URL is not left in production.

## 6. What is verified

- PVE can reach ntfy through the webhook target.
- PVE’s notification engine emits a successful test.
- ntfy accepts the PVE test with a small body and returns a message id.
- The production URL is restored.

## 7. What is not verified

- A **real CT104/VM200 backup completion** has not yet produced a post-repair notification, because the next backup window is after this repair and the baseline is blocked by storage.
- The production topic was not used for the test, by design.
- Email delivery is separately broken: Postfix bounces to an old Gmail address. This does not affect ntfy, but the `mail-to-root` fallback is not reliable.

## 8. Rollback

Restore the backup:

```bash
cp /root/notifications.cfg.backup-20261111T180707Z /etc/pve/notifications.cfg
```

or re-run `pvesh set` with the original body and URL. No ntfy or guest state is involved.

## 9. Follow-up for Freebuff

Implement a short structured PVE notification payload, for example:

```json
{
  "title": "vzdump OK — CT104",
  "message": "CT104 docker-stack: finished in 6m 12s, 33.8 GiB",
  "priority": 3,
  "tags": ["floppy_disk", "pve"],
  "click": "https://<pve-ui>/"
}
```

Keep the full log in PVE, not in ntfy. Send recovery and failure events to the approved `kf-backups` topic after the topic migration is authorized.
