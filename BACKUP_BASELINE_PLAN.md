# Backup Baseline Plan

**Baseline date:** 2026-11-12
**Status:** **NOT COMPLETE — CT100–CT103 only; CT104 and VM200 missing**
**Deletion prerequisite:** exact confirmation in `OLD_BACKUP_DELETION_MANIFEST.md`

## 1. Current schedules (unchanged)

`/etc/pve/vzdump.cron`:

```cron
0 1 * * *  root vzdump 100 103 --compress zstd --mode snapshot --quiet 1 --storage backup-hdd
30 1 * * * root vzdump 101 102 --compress zstd --mode snapshot --quiet 1 --storage backup-hdd
0 2 * * *  root vzdump 104 --storage backup-hdd --quiet 1 --mode snapshot --compress zstd
30 2 * * * root vzdump 200 --quiet 1 --compress zstd --mode snapshot --storage backup-hdd
```

`keep-last=2` on `backup-hdd`.

## 2. What already happened on 2026-11-12

| Guest | Result | Archive |
|---|---|---|
| CT100 | OK | 2026-11-12 01:00 |
| CT101 | OK | 2026-11-12 01:30 |
| CT102 | OK | 2026-11-12 01:31 |
| CT103 | OK | 2026-11-12 01:00 |
| CT104 | **FAILED** — disk quota | none |
| VM200 | **FAILED** — broken pipe at 76 % | none |

So the “2026-11-12 baseline” is **partial**. It must not be described as complete.

## 3. Capacity math

| Guest | Latest complete archive |
|---|---:|
| CT100 | 0.328 GiB |
| CT101 | 0.929 GiB |
| CT102 | 3.216 GiB |
| CT103 | 0.275 GiB |
| CT104 | 33.809 GiB |
| VM200 | 28.043 GiB |
| **One full generation** | **≈66.6 GiB** |

| Target | Capacity | Generations that fit | Notes |
|---|---:|---:|---|
| `backup-hdd` | 150 GiB | 2 | 133.2 GiB + filesystem overhead; 3 do not fit |
| `fast-data` (if converted to directory storage) | 238 GiB | 3 | 199.8 GiB; separate NVMe device |
| `local` root SSD | 94 GiB | 1 marginal | Not recommended; root filesystem risk |
| External/off-site | **unknown** | **0** | **No verified destination exists** |

A full GFS policy of daily 2 + weekly 2 + monthly 1 needs **≈333 GiB**, which no current target provides.

## 4. Options

### Option A — Recommended: second target before cleanup

1. Approve adding a directory storage target on the existing 238 GiB NVMe (`fast-data` thin pool).
2. Create a dedicated LV/filesystem, mount it, and add a PVE `dir` storage with content `backup`.
3. Run a test backup of CT100 and verify it.
4. Delete the old archives only after the test target is proven.
5. Baseline: keep 2 daily on the HDD and 3 weekly on the NVMe.
6. Copy one verified generation off the host as soon as an external destination exists.

This does **not** create an LXC/CT/VM. It uses the existing NVMe. It is a storage configuration change and needs explicit approval.

### Option B — Interim single-device risk

1. Confirm deletion of the 12 archives (and optional logs).
2. Run the full baseline on the same HDD immediately.
3. Label it **single-device, no off-site**.
4. Copy the new generation off the HDD immediately.
5. Replace the HDD as soon as practical.

This is the fastest route but deliberately accepts a single point of failure on an aging disk with historical uncorrectable events. **Not recommended as the final state.**

### Option C — Replace the HDD first

1. Install a replacement drive.
2. Create a new pool/dataset.
3. Delete old archives only after the new target is verified.
4. Run the baseline on the new drive.

Safest for data, but no new baseline until hardware is available.

## 5. Exact baseline commands after approval

Do not run these before the target is verified and the manifest is confirmed.

```bash
# Verify the target exists and is active
pvesm status -storage backup-hdd

# Small guest first
vzdump 100 --storage backup-hdd --mode snapshot --compress zstd --quiet 1

# Then the standard pairs
vzdump 101 102 --storage backup-hdd --mode snapshot --compress zstd --quiet 1
vzdump 103 --storage backup-hdd --mode snapshot --compress zstd --quiet 1

# Large guest last, with free space checked first
vzdump 104 --storage backup-hdd --mode snapshot --compress zstd --quiet 1
vzdump 200 --storage backup-hdd --mode snapshot --compress zstd --quiet 1
```

If the second NVMe target is approved, replace `backup-hdd` with the new storage ID for the weekly run, not for the daily run.

## 6. Verification per archive

| Check | Command / evidence |
|---|---|
| PVE task status | `pvesh get /nodes/pve/tasks --limit ...` → `OK` |
| Log | last line `Finished Backup` and no `ERROR` |
| zstd integrity | `zstd -t <archive>` for `.tar.zst` / `.vma.zst` |
| CT archive contents | `tar -tf <archive>` under a temp directory |
| VM archive contents | decompress to a temp file and `vma list <file>` |
| Storage listing | `pvesm list backup-hdd` shows the expected volume |
| Size sanity | archive size within ±10 % of the prior generation |

No current archive has an independent checksum; “finished” is a PVE log statement, not a cryptographic verification.

## 7. Retention

| Tier | Local HDD | NVMe (if approved) | Off-site |
|---|---:|---:|---:|
| Daily | 2 | — | — |
| Weekly | — | 3 | — |
| Monthly | — | — | **required later** |

- HDD: `keep-last=2`.
- NVMe: `keep-last=3` for weekly; do not claim monthly retention until an external target exists.
- Prune only terminal/complete archives; never delete an incomplete or actively written archive.
- Confirm the prune setting before enabling.

## 8. Failure handling

If a guest backup fails:

1. Do not delete any older archive for that guest.
2. Check `pvesm status` and free space.
3. Read the task log for `zstd`, `vma_queue_write`, `No space`, or quota errors.
4. Reduce the generation count or move to the second target; do not lower compression quality blindly.
5. Re-run only the failed guest.
6. A baseline is accepted only when **all six guests** have a verified 2026-11-12-or-later archive.

## 9. Off-site

No existing off-site destination was found. This remains an open requirement. A local second disk is not off-site. Do not describe the NVMe copy as disaster recovery.

## 10. Immediate next action

Owner selects Option A, B or C. Until then, the old archives remain untouched and the baseline remains **incomplete**.
