# Backup Capacity Model

**Phase:** A — read-only engineering calculation
**Date:** 2026-11-12
**Basis:** measured `pvesm`, `df`, `zfs`, and the twelve existing archive sizes. No retention is approved by this document.

## 1. Measured inputs

| Input | Measured value |
|---|---:|
| SSD raw namespace | 256,060,514,304 B = **238.47 GiB** |
| SSD LVM thin pool `fast-data-ssd/data` | **238.24 GiB** |
| SSD thin-pool data used | **0.00 %** |
| SSD thin-pool metadata used | **10.42 %** |
| HDD raw device | 500,103,634,432 B = **465.8 GiB** |
| HDD ZFS pool `safedrive` | 464 GiB, 142 GiB allocated, **307 GiB available** |
| `backup-hdd` dataset quota | **150 GiB** |
| `backup-hdd` used | **124.87 GiB** |
| `backup-hdd` available | **25.12 GiB** |
| Latest per-guest archives | **66.600 GiB** |
| All twelve historical archives | **124.802 GiB** |
| Largest single archive | **33.809 GiB** (CT104, 2026-10-20) |
| Second largest | **28.043 GiB** (VM200, 2026-11-10) |

### Latest generation calculation

| Guest | Latest archive | GiB |
|---|---|---:|
| CT100 | `vzdump-lxc-100-2026_11_12-01_00_01.tar.zst` | 0.328 |
| CT101 | `vzdump-lxc-101-2026_11_12-01_30_02.tar.zst` | 0.929 |
| CT102 | `vzdump-lxc-102-2026_11_12-01_30_18.tar.zst` | 3.216 |
| CT103 | `vzdump-lxc-103-2026_11_12-01_00_09.tar.zst` | 0.275 |
| CT104 | `vzdump-lxc-104-2026_10_20-02_00_02.tar.zst` | 33.809 |
| VM200 | `vzdump-qemu-200-2026_11_10-02_30_02.vma.zst` | 28.043 |
| **Total** | | **66.600** |

CT104 is 23–24 days old and VM200 is 2 days old at the time of this measurement. The next true generation may be larger; use **66.6 GiB as a lower-bound estimate**, not a guaranteed size.

## 2. SSD capacity scenarios

Assume ext4 on an LVM thin volume carved from the existing `fast-data-ssd` pool. Ext4 metadata, journal and reserved blocks consume roughly 1–2 % of the logical volume. The design reserve is **20 % of the filesystem**; a 15 % minimum is shown for comparison.

| Scenario | Logical volume | Usable estimate | 3 × 66.6 GiB | Free after 3 | Free % | 20 % reserve met? | 2 × 66.6 GiB | Free after 2 | Free % |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
| Full pool | 238.24 GiB | 235.0 GiB | 199.80 GiB | 35.2 GiB | **15.0 %** | **No** (needs 47.0) | 133.20 GiB | 101.8 GiB | 43.3 % |
| 220 GiB LV | 220.0 GiB | 217.0 GiB | 199.80 GiB | 17.2 GiB | **7.9 %** | **No** | 133.20 GiB | 83.8 GiB | 38.6 % |
| 200 GiB LV | 200.0 GiB | 197.0 GiB | 199.80 GiB | **negative** | — | **No** | 133.20 GiB | 63.8 GiB | 32.4 % |

**Conclusion:** three full generations of the current size **physically fit only when using essentially the entire SSD** (≈85 % used, ≈15 % free). That leaves no room for a fourth partial generation, no growth, and less than the 20 % safety reserve. It is **not a safe target**. Two generations are robust (≈57 % used, ≈43 % free).

The SSD also reports `Namespace 1 Utilization 192 GB`, although the thin pool reports 0 % data. This is a NAND/namespace allocation indicator, not a filesystem usage value, but it reinforces that the device must not be treated as a fresh empty disk. It is currently an active PVE `lvmthin` store with metadata already in use.

## 3. HDD capacity scenarios

Current quota is 150 GiB. The pool has 307 GiB free, so the dataset quota can be raised without adding hardware, but this is a **same-physical-disk** change, not redundancy.

| Scenario | Quota | 2 generations | Free after 2 | Free % | Can hold 2 old + new while copying? |
|---|---:|---:|---:|---:|---|
| Current | 150 GiB | 133.20 GiB | 16.80 GiB | **11.2 %** | **No** (3 × 66.6 = 199.8) |
| Expanded | 220 GiB | 133.20 GiB | 86.80 GiB | 39.5 % | **Yes**, with 20.2 GiB transient free |
| Expanded | 240 GiB | 133.20 GiB | 106.80 GiB | 44.5 % | **Yes**, with 40.2 GiB transient free |

**Conclusion:** two HDD generations fit in 150 GiB, but the safe copy order (keep both old generations, copy the new one, verify, then prune) does **not** fit. Either:

- raise `backup-hdd` quota to **at least 220 GiB** (240 GiB preferred), or
- accept a documented transient state with only one HDD generation while copying. This still preserves a good copy on the SSD, but it weakens secondary-device resilience.

The first option is recommended. Neither option makes the HDD independent.

## 4. Worst-case and growth model

| Model | Generation size | 2 SSD generations | 3 SSD generations | 2 HDD generations | 3 HDD during copy |
|---|---:|---:|---:|---:|---:|
| Minimum | 60 GiB | 120 GiB | 180 GiB | 120 GiB | 180 GiB |
| **Current** | **66.6 GiB** | **133.2 GiB** | **199.8 GiB** | **133.2 GiB** | **199.8 GiB** |
| +10 % growth | 73.3 GiB | 146.6 GiB | **219.9 GiB** | 146.6 GiB | 219.9 GiB |
| Worst case | 78 GiB | 156 GiB | **234 GiB** | 156 GiB | 234 GiB |
| Failure threshold (20 % reserve) | — | 188 GiB usable | **235 GiB usable** | 176 GiB usable | 193.6 GiB usable |

At 66.6 GiB, three SSD generations consume 84 % of a 235 GiB filesystem before manifests and logs. At 73.3 GiB (10 % growth), three consume 93.5 %, leaving 6.5 %. At 78 GiB, they consume 99.6 %, which is a failure condition.

**The 20 % reserve rule means the approved three-generation SSD policy cannot be met with the current SSD and current data size.** This is a capacity-driven blocker, not a preference.

## 5. Temporary and source-side space

- `vzdump` writes the final archive to the target. It does not require a second full copy on the target.
- Snapshot/COW data is created on the **source** storage, not the backup SSD. For CT/VM snapshot mode, monitor `local-lvm` and per-volume free space.
- `local-lvm` currently has **192.8 GiB** free (47.14 % used). CT104’s own rootfs is 100 GiB at 87.27 % used, leaving about 12.7 GiB free inside that filesystem; a snapshot is pool-backed, but a full CT104 filesystem is itself close to capacity.
- The HDD copy needs a complete second copy only when staging `new` alongside the old generation. That is why the 150 GiB quota is insufficient for the safe order.
- Manifests, SHA-256 files and logs are negligible (<10 MiB); host configuration is <1 MiB before encryption.

## 6. Actual usable capacity recommendation

| Target | Recommended allocation | Rationale |
|---|---|---|
| SSD primary | **200–210 GiB ext4 filesystem** | 2 × 66.6 GiB = 133.2 GiB, leaving 64–74 GiB (32–37 %) for growth, snapshots and reserve. |
| SSD if 3 generations are mandatory | **238 GiB, with a documented exception to the 20 % reserve** | Fits only at ~85 % used; requires a 10 % growth cap and active capacity alarms. Not recommended. |
| HDD secondary | **220–240 GiB quota** | 2 × 66.6 GiB = 133.2 GiB, with room to stage a third copy before pruning; 20–40 GiB transient free. |
| Off-site | **not present** | Required for real disaster recovery; no local calculation can substitute. |

## 7. Capacity gates for Phase D/H

The pipeline must refuse to start unless all are true:

1. `free_ssd >= estimated_generation × 1.20 + 20 GiB`.
2. `free_hdd >= estimated_generation × 1.20 + 10 GiB`.
3. The estimated generation is at least the previous verified generation × 1.10; otherwise require an explicit override.
4. No existing generation is pruned until the new generation is verified on both targets.
5. If any gate fails, stop and notify; do not reduce retention silently.

## 8. What must be approved

1. **Accept two SSD generations** (recommended), **or** approve the 238 GiB / 15 %-free exception for three generations with a growth cap and alarms.
2. **Raise `backup-hdd` quota to 220–240 GiB**, or accept the transient one-generation HDD copy order.
3. Confirm the SSD carve-out method: a new thin LV inside `fast-data-ssd`, formatted ext4 and mounted as a PVE `dir` backup target. This consumes thin-pool capacity but preserves the existing PVE `fast-data` definition and all guest disks.
4. Confirm that no off-site claim is made until an actual off-site destination is verified.

No storage change has been made.

---

# Revision 2 — Owner architecture update (2026-11-12)

**This revision supersedes the three-generation SSD scenarios and the two-generation HDD scenarios above.** The final target is now: **dedicated SSD with two generations, HDD with one latest generation plus all historical archives preserved.**

## R2.1 Revised SSD capacity

The SSD is converted from an empty `fast-data` LVM thin pool into one dedicated ext4 filesystem. Current dependency check found **no guest volume and no guest reference** to `fast-data`; `pvesm list fast-data` is empty.

| Item | Value |
|---|---:|
| SSD raw namespace | 238.47 GiB |
| Expected ext4 usable | ~235 GiB |
| Generation estimate | 66.6 GiB |
| 2 generations | 133.2 GiB |
| Free after 2 | ~101.8 GiB |
| Used | ~57 % |
| 20 % reserve | ~47 GiB; met |
| 10 % growth (73.3 × 2) | 146.6 GiB; ~88.4 GiB free |
| Worst case (78 × 2) | 156 GiB; ~79 GiB free |

**Two generations are safe. Three are no longer the target.** Even on the dedicated disk, 3 × 66.6 = 199.8 GiB leaves only ~35 GiB (15 %), below the 20 % reserve.

## R2.2 Revised HDD capacity

The owner direction is to increase the backup dataset by approximately 50 GB and recover it from the downloads dataset.

| Dataset | Current quota | Proposed quota | Used | Available after |
|---|---:|---:|---:|---:|
| `safedrive/downloads` | 200 GiB | **150 GiB** | 6.04 GiB | 143.96 GiB |
| `safedrive/backup` | 150 GiB | **200 GiB** | 125 GiB | 75 GiB |
| `safedrive/immichdrive` | 100 GiB | 100 GiB | 1.90 GiB | 98.1 GiB |

The pool has 307 GiB free and no dataset reservations. ZFS quotas are **limits, not allocated blocks**: reducing downloads does not free physical space; it transfers quota headroom and keeps total quotas below the 464 GiB pool.

### HDD with all 12 historical archives retained

| State | Calculation | Free in 200 GiB |
|---|---:|---:|
| History + one 66.6 GiB generation | 124.802 + 66.600 = **191.402 GiB** | **8.598 GiB (4.3 %)** |
| History + 10 % growth (73.3 GiB) | 124.802 + 73.3 = **198.1 GiB** | **1.9 GiB (0.95 %)** |
| History + worst case 78 GiB | 124.802 + 78 = **202.8 GiB** | **negative — fails** |

**200 GiB is the literal +50 GB target but is tight.** It supports one current-size generation only. A 220 GiB quota leaves ~28.6 GiB; 240 GiB leaves ~48.6 GiB. The owner must either accept a strict generation-size cap at 200 GiB or approve a larger quota transfer.

## R2.3 Revised capacity gates

1. SSD free `>= generation × 1.20` for a two-generation set (minimum ~20 GiB headroom).
2. HDD free `>= generation × 1.10` after history and previous generation are considered.
3. Refuse to start if the estimated generation exceeds 75 GiB at a 200 GiB HDD quota.
4. Never prune the last verified generation.
5. Do not claim the 3-generation target; it is superseded.
