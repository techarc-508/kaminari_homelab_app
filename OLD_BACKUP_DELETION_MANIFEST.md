# Old PVE Backup Deletion Manifest

**Prepared:** 2026-11-12
**Status:** **NOT EXECUTED — AWAITING EXACT OWNER CONFIRMATION**

This manifest is the complete list of PVE backup archives currently in `backup-hdd`. No other archive storage contains a known PVE backup generation.

## Scope A — 12 backup archives (the deletion target)

All paths are relative to storage `backup-hdd`; the local path is `/safedrive/backup/vzdump/dump/<file>`.

| # | Archive | Guest | Type | Date (mtime) | Size (bytes) | Size (GiB) | Integrity evidence | Only known recovery? |
|---:|---|---|---|---|---:|---:|---|---|
| 1 | `vzdump-lxc-100-2026_11_11-01_00_02.tar.zst` | CT100 adguard | LXC | 2026-11-11 01:00 | 350,838,613 | 0.327 | PVE log says finished; no checksum | One of two CT100 copies; no off-site |
| 2 | `vzdump-lxc-100-2026_11_12-01_00_01.tar.zst` | CT100 adguard | LXC | 2026-11-12 01:00 | 351,734,120 | 0.328 | PVE log says finished; no checksum | One of two CT100 copies; no off-site |
| 3 | `vzdump-lxc-101-2026_11_11-01_30_01.tar.zst` | CT101 guacamole | LXC | 2026-11-11 01:30 | 997,299,356 | 0.929 | PVE log says finished; no checksum | One of two CT101 copies; no off-site |
| 4 | `vzdump-lxc-101-2026_11_12-01_30_02.tar.zst` | CT101 guacamole | LXC | 2026-11-12 01:30 | 997,305,490 | 0.929 | PVE log says finished; no checksum | One of two CT101 copies; no off-site |
| 5 | `vzdump-lxc-102-2026_11_11-01_30_18.tar.zst` | CT102 immich | LXC | 2026-11-11 01:31 | 3,453,132,651 | 3.216 | PVE log says finished; no checksum | One of two CT102 copies; no off-site |
| 6 | `vzdump-lxc-102-2026_11_12-01_30_18.tar.zst` | CT102 immich | LXC | 2026-11-12 01:31 | 3,452,984,986 | 3.216 | PVE log says finished; no checksum | One of two CT102 copies; no off-site |
| 7 | `vzdump-lxc-103-2026_11_11-01_00_10.tar.zst` | CT103 cloudflared | LXC | 2026-11-11 01:00 | 295,628,223 | 0.275 | PVE log says finished; no checksum | One of two CT103 copies; no off-site |
| 8 | `vzdump-lxc-103-2026_11_12-01_00_09.tar.zst` | CT103 cloudflared | LXC | 2026-11-12 01:00 | 295,698,139 | 0.275 | PVE log says finished; no checksum | One of two CT103 copies; no off-site |
| 9 | `vzdump-lxc-104-2026_10_19-02_00_02.tar.zst` | CT104 docker-stack | LXC | 2026-10-19 02:08 | 29,653,526,590 | 27.617 | PVE log says finished; no checksum | One of two CT104 copies; **newest is 2026-10-20** |
| 10 | `vzdump-lxc-104-2026_10_20-02_00_02.tar.zst` | CT104 docker-stack | LXC | 2026-10-20 02:08 | 36,301,750,443 | 33.809 | PVE log says finished; no checksum | **Latest CT104 recovery point**; no off-site |
| 11 | `vzdump-qemu-200-2026_11_09-02_30_01.vma.zst` | VM200 linuxmint | QEMU | 2026-11-09 02:35 | 27,744,324,159 | 25.839 | PVE log says finished; no checksum | One of two VM200 copies; no off-site |
| 12 | `vzdump-qemu-200-2026_11_10-02_30_02.vma.zst` | VM200 linuxmint | QEMU | 2026-11-10 02:36 | 30,110,547,015 | 28.043 | PVE log says finished; no checksum | **Latest VM200 recovery point**; no off-site |

**Totals:** 12 archives · **134,004,769,785 bytes** · **124.802 GiB**.

No `.dat`, `.tmp`, `.partial`, or `.part` files were found. There are no incomplete archive artifacts to remove.

## Scope B — associated logs (optional, recommended for a clean baseline)

| # | Log | Size (bytes) |
|---:|---|---:|
| 1 | `vzdump-lxc-100-2026_11_11-01_00_02.log` | 1,006 |
| 2 | `vzdump-lxc-100-2026_11_12-01_00_01.log` | 1,006 |
| 3 | `vzdump-lxc-101-2026_11_11-01_30_01.log` | 1,015 |
| 4 | `vzdump-lxc-101-2026_11_12-01_30_02.log` | 1,015 |
| 5 | `vzdump-lxc-102-2026_11_11-01_30_18.log` | 1,100 |
| 6 | `vzdump-lxc-102-2026_11_12-01_30_18.log` | 1,100 |
| 7 | `vzdump-lxc-103-2026_11_11-01_00_10.log` | 1,009 |
| 8 | `vzdump-lxc-103-2026_11_12-01_00_09.log` | 1,009 |
| 9 | `vzdump-lxc-104-2026_10_19-02_00_02.log` | 1,400 |
| 10 | `vzdump-lxc-104-2026_10_20-02_00_02.log` | 1,400 |
| 11 | `vzdump-qemu-200-2026_11_09-02_30_01.log` | 7,300 |
| 12 | `vzdump-qemu-200-2026_11_10-02_30_02.log` | 7,400 |

**Total:** 12 logs · **25,957 bytes** (sizes rounded in the filesystem listing; exact sum recorded from `stat`).

## Expected effect

| Item | Before | After Scope A+B |
|---|---:|---:|
| `backup-hdd` used | 130,948,736 KiB (~124.9 GiB) | ~0 KiB |
| `backup-hdd` available | 26,337,664 KiB (25.1 GiB) | ~157,286,000 KiB (~149.9 GiB) |
| Archive count | 12 | 0 |
| Log count | 12 | 0 |

The storage stays online. No guest disk, Docker volume, ZFS dataset, ISO, template, or PVE storage definition is in scope.

## Deletion boundary

**Allowed only after confirmation:**

- The 12 archives in Scope A.
- The 12 logs in Scope B if explicitly included.

**Never in scope:**

- CT/VM rootfs or disk images.
- Docker volumes or bind-mounted application data.
- `safedrive/downloads`, `safedrive/immichdrive`, or `/data/annotations`.
- ZFS datasets, pools, quotas, or snapshots.
- ISOs, templates, or PVE storage definitions.

## Exact confirmation requested

Reply with exactly one of:

```text
CONFIRMED: delete 12 archives only (Scope A)
```

or

```text
CONFIRMED: delete 12 archives + 12 logs (Scope A+B)
```

No deletion will occur before that reply. The confirmation is for this manifest only; it will not be requested again for the same list.
