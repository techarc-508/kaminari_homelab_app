# Stage 1 — Backup Recovery and Storage Report

**Date:** 2026-11-12
**Scope:** Read-only diagnosis, exact backup inventory, PVE notification repair, baseline planning
**Status:** **PARTIAL — ARCHIVE CLEANUP NOT EXECUTED; TRUSTWORTHY BASELINE BLOCKED**

No guest disk, Docker volume, ZFS dataset, ISO, template, ntfy data, website database or application file was deleted. No backup archive was deleted. No PVE storage definition was removed. No account, token or topic was created.

## 1. What was verified

| Item | Evidence |
|---|---|
| PVE version | 9.2.11, single node `pve` |
| Guest set | CT100, CT101, CT102, CT103, CT104, VM200 — all running |
| Backup target | `backup-hdd` (`dir`, `/safedrive/backup/vzdump`), 150 GiB quota |
| Archives present | **12** complete archives, **124.802 GiB** |
| Associated logs | **12** logs, **25,957 bytes** |
| Incomplete artifacts | **None found** (`.dat`, `.tmp`, `.partial`, `.part`) |
| Backup schedule | Four daily `vzdump` cron jobs at 01:00, 01:30, 02:00, 02:30 |
| Storage retention | `keep-last=2` on `backup-hdd` |
| Latest CT100–CT103 backup | **2026-11-12 01:00–01:31 — OK** |
| Latest CT104 success | **2026-10-20 02:08** |
| Latest VM200 success | **2026-11-10 02:36** |
| CT104 2026-11-12 | **FAILED** — `zstd: error 70: Disk quota exceeded` |
| VM200 2026-11-12 | **FAILED** — `vma_queue_write: write error - Broken pipe` at 76 % |
| Free before cleanup | **25.1 GiB** of 150 GiB (83.25 % used) |
| Expected free after archive+log retirement | **≈149.9 GiB** |

## 2. Exact current backup state

The full archive list is in [`OLD_BACKUP_DELETION_MANIFEST.md`](OLD_BACKUP_DELETION_MANIFEST.md).

Important distinction: the 2026-11-12 backups of CT100–CT103 are **new successful backups**, but they are not a complete baseline because CT104 and VM200 failed. CT104’s newest recovery point is from **2026-10-20**; VM200’s newest is **2026-11-10**. Those are stale recovery points for the two largest workloads.

## 3. Why the backups fail

### CT104

The exact task log ends with:

```
zstd '--threads=1' >/safedrive/backup/vzdump/dump/vzdump-lxc-104-2026_11_11-02_00_02.tar.dat
failed: exit code 70
INFO: Failed at 2026-11-11 02:07:11
cp: cannot create regular file '...2026_11_11-02_00_02.log': Disk quota exceeded
```

`zstd` exit 70 is a write-side disk error. The backup target had only ~25 GiB free while the previous CT104 archive was ~34 GiB. The failure is **capacity exhaustion**, not a CT104 filesystem quota.

### VM200

The 2026-11-12 task log shows progress to 76 % of 180 GiB, then:

```
ERROR: Backup of VM 200 failed - vma_queue_write: write error - Broken pipe
```

The archive was not completed. The backup was writing a 130 GiB VM disk plus a 50 GiB secondary disk while the target was nearly full. The broken pipe is the VMA writer losing its destination; it is consistent with the target running out of space, not with a VM disk read failure. No kernel `ENOSPC` line was captured, so this is an evidence-based inference, not a kernel-confirmed cause.

### Common cause

Both large jobs were scheduled to write into a **150 GiB target already holding ~125 GiB**. They cannot complete reliably with the current two-generation retention.

## 4. Normalization performed

No configuration was changed except the PVE notification target described below.

- The current `/etc/pve/vzdump.cron` schedules remain in place.
- `backup-hdd` retention remains `keep-last=2`.
- No archive was removed.
- No guest was stopped, restarted or reconfigured.

## 5. PVE notification repair

See [`PVE_NOTIFICATION_REPAIR_REPORT.md`](PVE_NOTIFICATION_REPAIR_REPORT.md).

Cause: the webhook body was the **full PVE task log**. VM200’s log was 7,556 bytes; ntfy rejects a body above 4,095 bytes with HTTP 400 (`invalid request: attachments not allowed`). CT104’s smaller log passed. Fix: body template reduced to `{{ title }}` only.

Verified through PVE’s own notification path to `kaminari-onboarding-test`; response was accepted and the test message was read back. Production URL was restored immediately.

## 6. Deletion decision — NOT EXECUTED

The exact manifest is presented in `OLD_BACKUP_DELETION_MANIFEST.md`.

**No deletion has been performed because the brief requires one final exact confirmation after the manifest is presented, and because the storage-health finding below changes the risk of destroying the last recovery points.**

## 7. Storage-health blocker

The archive disk is a **single ST500DM002-1BD142 500 GB HDD** with:

- SMART overall: **PASSED**
- Power-on hours: **29,108**
- `Reported_Uncorrect`: **11,773**
- `Raw_Read_Error_Rate`: 117
- `Seek_Error_Rate`: 78
- `UDMA_CRC_Error_Count`: 2
- Reallocated sectors: **0**
- Pending sectors: **0**
- Offline uncorrectable: **0**
- ZFS pool: `ONLINE`, scrub clean, 0 data errors

This is **not currently failing** by the minimal SMART verdict, but it is an aging single disk with a large historical uncorrectable-event count and **no redundancy and no verified off-site copy**. It is not a trustworthy sole backup target.

Full detail: [`STORAGE_HEALTH_REPORT.md`](STORAGE_HEALTH_REPORT.md).

## 8. Baseline status

The new 2026-11-12 baseline is **not complete**:

- CT100–CT103: successful 2026-11-12 archives exist.
- CT104: no 2026-11-12 archive; last success 2026-10-20.
- VM200: no 2026-11-12 archive; last success 2026-11-10.
- The archive target cannot hold two full generations plus growth.

A trustworthy baseline is **blocked** until one of these is approved:

1. **Preferred:** add a second, healthy backup target (existing NVMe or external disk) and keep the HDD as a secondary copy.
2. **Accept-risk interim:** delete the old archives, run the baseline on the same HDD, and immediately copy the new baseline off the disk. This is a single-device recovery point and must be labeled as such.
3. **Stop:** replace the HDD before creating the baseline. Safest for data, but no new backup until the replacement is installed.

See [`BACKUP_BASELINE_PLAN.md`](BACKUP_BASELINE_PLAN.md).

## 9. Acceptance checklist

| Criterion | Status |
|---|---|
| Complete archive inventory | ✅ |
| Exact deletion manifest | ✅ |
| Storage and SMART understood | ✅ |
| Deletion scope reviewed | ⏸ Awaiting exact owner confirmation |
| Archive cleanup complete | ❌ Not executed |
| Capacity reclaimed | ❌ Not applicable until cleanup |
| CT104/VM200 failures diagnosed | ✅ |
| PVE notification 400 repaired | ✅ PVE path tested to onboarding topic |
| New baseline scheduled | ✅ 2026-11-12 cron already ran; full baseline incomplete |
| No non-backup data touched | ✅ |
| No secret exposed | ✅ |

## 10. Rollback

Before any future cleanup, PVE has a notification-config backup at:

```
/root/notifications.cfg.backup-20261111T180707Z
```

The backup archives are immutable files; no PVE storage or guest state was changed. If a future cleanup is approved, it is irreversible for those archives. The rollback for the notification change is restoring that file and restarting the PVE notification stack, or re-running `pvesh set` with the original URL/body.

## 11. Recommendation

**Do not delete the 12 archives yet.** First choose a second backup target. The current HDD is the only copy of the last known recovery points for CT104 and VM200, and it has a historical uncorrectable-event count of 11,773. Deleting those archives before a verified replacement target exists would leave the homelab with **no recent recoverable copy** of its two largest workloads.
