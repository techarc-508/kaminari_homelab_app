# Backup Device Discovery

**Phase:** A — read-only discovery
**Date:** 2026-11-12
**Scope:** Device identity, current use, health and capacity. No formatting, partitioning, mounting, storage changes or writes.

## 1. Candidate devices

| Role | Device | Model | Serial | Size | Interface | Current use |
|---|---|---|---|---:|---|---|
| **Primary candidate** | `nvme0n1` | **XPG GAMMIX S11 Pro** | `…7027` (masked) | **238.5 GiB** (256 GB namespace) | NVMe PCIe | LVM2 physical volume `fast-data-ssd`; thin pool `data`; PVE storage `fast-data` (`images,rootdir`) |
| **Secondary** | `sda` | **ST500DM002-1BD142** | `…ZE5` (masked) | **465.8 GiB** | SATA HDD | ZFS member `safedrive`; PVE `backup-hdd`; also downloads/Immich datasets |
| Not a target | `nvme1n1` | SAMSUNG MZVL4512HBLU-00BH1 | `…9308` (masked) | 476.9 GiB | NVMe PCIe | PVE root, `local-lvm`, guest disks |

`zd0` (50 GiB) is the ZFS zvol for VM200’s second disk, not a physical device.

## 2. Primary SSD in detail

### Identity and layout

- Model: **XPG GAMMIX S11 Pro**
- Serial: `…7027` (masked)
- Firmware: `42B2S7JA`
- Capacity: 256,060,514,304 bytes; **238.5 GiB** usable namespace
- Interface: NVMe 1.3
- Partition/filesystem: **LVM2_member**, no ordinary partition table
- Volume group: `fast-data-ssd`
- Thin pool: `data`, **238.24 GiB**
- Current data occupancy: **0.00 %**
- Thin metadata occupancy: **10.42 %**
- PVE storage: `fast-data` (`lvmthin`, content `images,rootdir`) — **not currently a backup target**
- Mount point: none as a directory

The SSD is **not empty** in the operational sense: it already carries an initialized LVM thin pool and PVE storage definition. It must not be formatted or repartitioned. A future backup target should be carved from the existing free thin-pool capacity or from a new LV, not by wiping the device.

### Health

| Metric | Value |
|---|---:|
| SMART overall | **PASSED** |
| Critical warning | 0x00 |
| Temperature | 36 °C |
| Available spare | 100 % (threshold 10 %) |
| Percentage used | **11 %** |
| Power-on hours | **9,218** |
| Power cycles | 1,492 |
| Unsafe shutdowns | 933 |
| Data read | 32.2 TB |
| Data written | **12.6 TB** |
| Media/data-integrity errors | **0** |
| Error-log entries | 0 |

`smartctl -i` also reports **Namespace 1 Utilization 192,361,959,424 B (192 GB)** while the LVM thin pool reports **0.00 %** data use. The two measurements describe different layers (NAND/namespace allocation versus host thin-pool usage), but they are a warning against treating the device as a fresh empty disk. The pool is already active PVE storage.

This is a healthy consumer NVMe with moderate wear. It is suitable as the primary backup target **only if** the capacity model in `BACKUP_CAPACITY_MODEL.md` is respected.

## 3. Secondary HDD in detail

- Model: **ST500DM002-1BD142**
- Serial: `…ZE5` (masked)
- Capacity: **465.8 GiB**
- Interface: SATA, rotating media
- Layout: ZFS member (`sda1`, 465.7 GiB), plus `sda9` 8 MiB
- Pool: `safedrive` — **ONLINE**, scrub clean, 0 data errors
- Dataset: `safedrive/backup`, quota **150 GiB**, used **125 GiB**, available **25.1 GiB**
- PVE storage: `backup-hdd` (`dir`, `/safedrive/backup/vzdump`, content `backup`, `keep-last=2`)
- Other data on the same physical disk: `safedrive/downloads` (6.04 GiB), `safedrive/immichdrive` (1.9 GiB), VM200 disk snapshot/zvol

### Health

| Metric | Value |
|---|---:|
| SMART overall | **PASSED** |
| Power-on hours | **29,127** |
| Reported uncorrectable events | **11,773** |
| Raw read error rate | 178,081,832 (normalized 118) |
| Seek error rate | 43,670,659,811 (normalized 78) |
| Reallocated sectors | 0 |
| Current pending sectors | 0 |
| Offline uncorrectable | 0 |
| UDMA CRC errors | 2 |
| Temperature | 39 °C (min/max 29/41) |

**Conclusion:** not in a current failure state, but aging and historically error-prone. It is acceptable only as a **secondary copy**, never as the only copy, and only with independent verification after every copy.

## 4. Existing storage map

| PVE storage | Type | Total | Used | Available | Content | Backup role |
|---|---|---:|---:|---:|---|---|
| `fast-data` | lvmthin | 238.24 GiB | 0 | 238.24 GiB | images,rootdir | **candidate primary** |
| `backup-hdd` | dir | 150 GiB | 124.87 GiB | 25.12 GiB | backup | current secondary/only |
| `local` | dir | 94 GiB | 13.4 GiB | 75.7 GiB | import,iso,vztmpl,backup | not a target |
| `local-lvm` | lvmthin | 347.9 GiB | 47.14 % | 183.9 GiB | images,rootdir | guest disks/snapshots |
| `safedrive` | zfspool | 449.7 GiB | 142 GiB | 307 GiB | images,rootdir | guest disks |
| `safedrive/backup` | ZFS dataset | 150 GiB quota | 125 GiB | 25.1 GiB | backup | current |

## 5. What must happen before Phase B

1. Owner approves using the SSD as the primary backup target.
2. Owner approves the exact storage design: a new LV + ext4 directory target on `fast-data`, or another non-destructive carve-out.
3. Owner approves expanding `backup-hdd` quota if two generations are required with a real reserve.
4. Owner confirms that no existing SSD data may be reformatted; the design will preserve the current thin pool.
5. A dry-run capacity and mount test is approved before any production backup.

## 6. Read-only commands used

`lsblk`, `pvs`, `vgs`, `lvs`, `zpool status`, `zfs list`, `df -hT`, `pvesm status`, `smartctl -i/-H/-A` on `/dev/nvme0n1`, `/dev/nvme1n1`, `/dev/sda`.

No write command was run.
