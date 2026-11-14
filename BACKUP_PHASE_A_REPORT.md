# Backup Phase A Report

**Phase:** A — read-only discovery and design
**Date:** 2026-11-12
**Status:** **COMPLETE — PASS WITH CONDITIONS; BLOCKED BEFORE PHASE B BY CAPACITY DECISIONS**
**Implementation:** **none.** No file was formatted, partitioned, mounted, created, resized, deleted or scheduled. No production backup was run.

## 1. Objective

Establish the real devices, guests, archives, capacity, health and constraints for an SSD-primary / HDD-secondary rolling backup system, and propose a safe architecture before any change.

## 2. Deliverables

| Document | Purpose |
|---|---|
| `BACKUP_DEVICE_DISCOVERY.md` | Exact SSD, HDD and non-target devices; identity, layout, health, current use |
| `BACKUP_GUEST_INVENTORY.md` | Six current guests, eligibility, method, archive size, exclusions, consistency |
| `BACKUP_CAPACITY_MODEL.md` | Measured generation size, usable capacity, reserve, worst case, HDD staging |
| `BACKUP_ARCHITECTURE_PROPOSAL.md` | Target layout, state machine, daily order, manifest, retention, restore, cloud readiness |
| `BACKUP_RISK_REGISTER.md` | BR-01…BR-25 with severity and required response |
| `BACKUP_PHASE_A_REPORT.md` | This report |

## 3. Actual devices

| Role | Device | Model | Size | Health / use |
|---|---|---|---:|---|
| **Primary candidate** | `nvme0n1` | **XPG GAMMIX S11 Pro** | **238.47 GiB** | SMART PASSED; 11 % used; 9,218 h; 0 media errors; 36 °C; LVM PV `fast-data-ssd`, thin pool 238.24 GiB at **0 % data**; PVE `fast-data` (`images,rootdir`) |
| **Secondary** | `sda` | **ST500DM002-1BD142** | **465.8 GiB** | SMART PASSED; **29,127 h**; `Reported_Uncorrect` **11,773**; 0 reallocated/pending; ZFS `safedrive` ONLINE, scrub clean; `backup-hdd` 150 GiB quota, 124.87 GiB used |
| Not a target | `nvme1n1` | SAMSUNG MZVL4512HBLU-00BH1 | 476.9 GiB | PVE root, `local-lvm`, guest disks; only 16 GiB VG free |

The SSD is **not empty**. It is an active LVM thin pool and PVE storage. A backup target must be carved as a new thin LV, not by formatting the device.

## 4. Actual guest coverage

| Guest | Latest archive | GiB | Status | Gap |
|---|---|---:|---|---|
| CT100 adguard | 2026-11-12 | 0.328 | OK | None |
| CT101 guacamole | 2026-11-12 | 0.929 | OK | DB consistency not application-aware |
| CT102 immich | 2026-11-12 | 3.216 | OK | DB consistency not application-aware; upload dataset included |
| CT103 cloudflared | 2026-11-12 | 0.275 | OK | Secrets inside archive |
| CT104 docker-stack | **2026-10-20** | 33.809 | **Stale/failing** | Four `backup=0` mounts; 56 containers; DB consistency |
| VM200 linuxmint | **2026-11-10** | 28.043 | **Stale/failing** | Two disks; snapshot/agent consistency |
| **Total** | | **66.600** | | |

## 5. Capacity conclusions

### Three SSD generations

**Not safely feasible.** 3 × 66.6 = **199.8 GiB**. On a 235 GiB usable ext4 filesystem that is **85 % used**, leaving ~35 GiB (15 %). The design reserve is 20 % (47 GiB), and a 10 % growth in the generation would consume 219.9 GiB, leaving ~6.5 %. It fits physically only by accepting a 15 % or lower reserve and enforcing a growth cap. **That exception is not approved.**

### Two SSD generations

**Feasible.** 2 × 66.6 = **133.2 GiB**, leaving ~102 GiB (43 %) on a 235 GiB filesystem. This is the recommended baseline unless the owner explicitly accepts the three-generation capacity risk.

### Two HDD generations

**Feasible after deleting the old archives**, but **not with the current 150 GiB quota during a safe copy**. 2 × 66.6 = 133.2 GiB; free = 16.8 GiB (11.2 %). The safe order needs two old + one new = 199.8 GiB, which exceeds 150 GiB. Raise the quota to **220–240 GiB**, or accept a one-generation transient copy state. The HDD pool has 307 GiB free, so a quota increase is technically possible but does **not** add redundancy.

### HDD safety

The HDD is **not safe as a sole or primary backup**. It is acceptable only as a **secondary copy** with independent SHA-256 verification, SMART/scrub monitoring and a replacement plan. `Reported_Uncorrect` 11,773 and 29,127 hours are material. The pool is a single disk; “two copies” on it are not disaster recovery.

## 6. Blocking conditions

| Blocker | Effect | Resolution |
|---|---|---|
| BR-01 three-generation capacity | Cannot meet 3 SSD + 20 % reserve | Approve 2 generations or a documented 3-generation exception with growth cap |
| BR-02 HDD quota | Safe copy order cannot fit 150 GiB | Approve 220–240 GiB quota or one-generation transient |
| BR-03 same physical disk | HDD does not survive disk loss | Off-site copy remains mandatory |
| BR-06 unverified archives | No proof of recoverability | SHA-256, format test, manifest, restore drill |
| BR-07 CT104 exclusions | CT104 archive is not a full application backup | Separate bind-mount/dataset backup and DB dumps |
| BR-10 SSD carve-out | Consumes empty `fast-data` pool and future guest capacity | Approve LV size and trade-off |
| BR-11 SSD namespace utilization | 192 GB NAND allocation vs 0 % host pool | Investigate/benchmark before relying on full capacity |

## 7. Exact commands used (read-only)

```text
lsblk -b -o NAME,MAJ:MIN,RM,SIZE,RO,TYPE,FSTYPE,MOUNTPOINTS,MODEL,SERIAL,ROTA,TRAN
pvs, vgs, lvs, zpool status -v, zpool get failmode,autotrim,ashift safedrive
zfs list, zfs get quota,used,available safedrive/backup
df -hT, pvesm status, cat /etc/pve/storage.cfg
pct list, qm list, pct config 102/104, qm config 200, qm agent 200 ping
find /safedrive/backup/vzdump ... (names, sizes, mtimes)
smartctl -i/-H/-A /dev/nvme0n1 /dev/nvme1n1 /dev/sda
cat /etc/pve/jobs.cfg, /etc/pve/vzdump.cron
```

No formatting, mkfs, mount, lvcreate, zfs set, pvesm set, vzdump, prune, delete, restore or schedule change was executed.

## 8. Answers required by the brief

1. **Are three SSD generations realistically feasible?** **No, not safely with a 20 % reserve and current 66.6 GiB generation.** Two are recommended. Three require an explicit exception at ~85 % used and a growth cap.
2. **Are two HDD generations realistically feasible?** **Yes after cleanup, but only with a 220–240 GiB quota for the safe copy order; 150 GiB supports two stable generations but not two old + new.**
3. **Is the current HDD safe enough for secondary copies?** **Only as a secondary, verified copy.** It is aging with 11,773 reported uncorrectable events and is a single disk; it is not suitable as the only backup.
4. **What is the exact SSD?** **`nvme0n1`, XPG GAMMIX S11 Pro, 256,060,514,304 B (238.47 GiB), firmware 42B2S7JA, serial …7027 (masked), LVM PV `fast-data-ssd`, thin pool 238.24 GiB at 0 % data, 11 % wear, 9,218 h, 0 media errors.**
5. **What must be approved before Phase B?**
   - 2 vs 3 SSD generations (and any reserve exception).
   - SSD LV size and the loss of future `fast-data` capacity.
   - HDD quota increase to 220–240 GiB, or one-generation transient.
   - Application-aware backup for Immich/Docker and separate coverage of CT104 bind mounts.
   - Encryption/key custody for host config.
   - Restore-drill target (new VMID/isolated bridge).
   - Whether the old 12 archives may be deleted (Scope A or A+B) — still **not authorized**.

## 9. Verdict

**PASS WITH CONDITIONS.** Discovery is complete and the architecture is technically coherent, but Phase B must not begin until the capacity and retention decisions above are approved. **No fresh baseline exists yet**; the 2026-11-12 run covers only CT100–CT103 and is partial. Do not describe it as a complete baseline.
