# Storage Health Report

**Date:** 2026-11-12
**Scope:** Existing PVE storage used by backups; no destructive tests, no disk writes, no pool changes.

## 1. Physical layout

| Device | Model | Size | Media | Role |
|---|---|---:|---|---|
| `sda` | ST500DM002-1BD142 | 465.8 GB | 7200 rpm HDD | Single-disk ZFS pool `safedrive`; hosts `safedrive/backup` |
| `nvme0n1` | XPG GAMMIX S11 Pro | 238.5 GB | NVMe SSD | LVM thin `fast-data`; currently not a backup target |
| `nvme1n1` | SAMSUNG MZVL4512HBLU-00BH1 | 476.9 GB | NVMe SSD | PVE root, `local-lvm`, guest disks |

There is **no hardware RAID** and no second copy of the backup dataset.

## 2. ZFS state

```
pool: safedrive
state: ONLINE
scan: scrub repaired 0B with 0 errors
errors: No known data errors
```

Datasets:

| Dataset | Used | Available | Quota | Notes |
|---|---:|---:|---:|---|
| `safedrive/backup` | 125 GiB | 25.1 GiB | 150 GiB | PVE `backup-hdd` target |
| `safedrive/downloads` | — | ~194 GiB | — | Application data; not a backup |
| `safedrive/immichdrive` | — | ~98.5 GiB | — | Immich uploads; not a backup |
| `safedrive` pool | — | ~307 GiB | — | Free pool space, same physical disk |

The pool is healthy at the ZFS level today. That does **not** make the single HDD a trustworthy backup destination.

## 3. SMART evidence

| Attribute | Value |
|---|---:|
| Overall SMART health | **PASSED** |
| Power-on hours | **29,108** (~3.32 years) |
| `Reported_Uncorrect` | **11,773** |
| `Raw_Read_Error_Rate` | 117 |
| `Seek_Error_Rate` | 78 |
| `UDMA_CRC_Error_Count` | 2 |
| `Reallocated_Sector_Ct` | 0 |
| `Current_Pending_Sector` | 0 |
| `Offline_Uncorrectable` | 0 |
| Last logged SMART error | at **19,868** power-on hours (about 9,240 hours before this report) |

**Interpretation:** the drive is not in a current failure state by the minimal SMART verdict, but it has a large historical uncorrectable-event count, 29k+ hours, and no redundancy. It is **aging/at-risk**, not “healthy enough to be the only backup target.” A PASSED summary must not be used to overrule the uncorrectable-event history.

## 4. Capacity

| Target | Total | Used | Available | Can hold a full new baseline? |
|---|---:|---:|---:|---|
| `backup-hdd` (`safedrive/backup`) | 150 GiB | 124.9 GiB | 25.1 GiB | **No** until old archives are removed |
| `local` (`/var/lib/vz`, root SSD) | 94 GiB | ~15 GiB | ~79 GiB | Marginal; one generation ~66.6 GiB, but unsafe for root |
| `local-lvm` | 347.9 GiB thin | 47 % | — | **Not a backup content type** |
| `fast-data` NVMe | 238 GiB thin | 0 % | 238 GiB | **Potentially yes**, but currently `images,rootdir` only; would need a new directory target |
| `safedrive` pool free | 465 GiB | — | ~307 GiB | Same physical HDD; not a second device |

### New baseline space estimate

Using the latest complete archives per guest:

| Guest | Latest size |
|---|---:|
| CT100 | 0.328 GiB |
| CT101 | 0.929 GiB |
| CT102 | 3.216 GiB |
| CT103 | 0.275 GiB |
| CT104 | 33.809 GiB (2026-10-20) |
| VM200 | 28.043 GiB (2026-11-10) |
| **Full generation** | **≈66.6 GiB** |

- 150 GiB target: **2 generations** (133.2 GiB) fit; 3 do not.
- 238 GiB NVMe target: **3 generations** (199.8 GiB) fit.
- Daily + weekly + monthly (5 generations) = **≈333 GiB**; neither target alone can hold it.

## 5. Backup scope caveats

- CT104 has four bind mounts marked `backup=0`: downloads, backup, Immich drive, and annotations. Its archive covers the **root filesystem only**; Docker application data on those mounts is not in the archive.
- VM200 has two disks: `scsi0` (130 GiB) and `scsi1` (50 GiB); both are in the snapshot backup.
- CT102 has an Immich upload mount; its backup inclusion must be confirmed before relying on it as a photo recovery point.

## 6. Conclusions

1. The **current backup target is full enough to cause both large-job failures**; this is the direct cause of the CT104 and VM200 failures.
2. The HDD is **not a trustworthy sole target**. ZFS being clean today does not address a 11,773 uncorrectable-event history or the single-device risk.
3. The safest Stage 1 path is to **add a second healthy target before deleting the old archives**. The existing 238 GiB NVMe can potentially become a directory-backed target, but that is a storage configuration change requiring owner approval and a verification test.
4. If the owner chooses the interim risk path, the new baseline must be labeled **single-device, short-term**, and copied off the HDD immediately.

## 7. Not done

- No SMART self-test was started.
- No `badblocks`, surface scan, or destructive test was run.
- No ZFS scrub was started or cancelled.
- No quota, dataset, storage definition, or pool was changed.
