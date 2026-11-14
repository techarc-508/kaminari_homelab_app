# Backup Guest Inventory

**Phase:** A — read-only
**Date:** 2026-11-12
**Scope:** All current CTs/VMs, backup eligibility, expected size, latest actual backup, verification status.

## 1. Current guests

| ID | Name | Type | Status | Backup eligible | Method | Actual filesystem use | Latest archive | Result | Verification | Notes |
|---:|---|---|---|---|---|---:|---|---|---|---|
| 100 | adguard | LXC | running, onboot | **Yes** | snapshot | 1.5 GiB of 7.8 GiB | `vzdump-lxc-100-2026_11_12-01_00_01.tar.zst` — 0.328 GiB | OK | PVE log says finished; no SHA-256 in archive | DNS service; small |
| 101 | apache-guacamole | LXC | running, onboot | **Yes** | snapshot | 2.6 GiB of 3.9 GiB | `vzdump-lxc-101-2026_11_12-01_30_02.tar.zst` — 0.929 GiB | OK | PVE log says finished; no SHA-256 | Remote access; credentials inside guest |
| 102 | immich | LXC | running, onboot | **Yes** | snapshot | 9.2 GiB root + 1.9 GiB upload | `vzdump-lxc-102-2026_11_12-01_30_18.tar.zst` — 3.216 GiB | OK | PVE log says finished; no SHA-256 | `mp0` `/opt/immich/upload` is **included** (no `backup=0`); database consistency must be verified |
| 103 | cloudflared | LXC | running, onboot | **Yes** | snapshot | 879 MiB of 3.9 GiB | `vzdump-lxc-103-2026_11_12-01_00_09.tar.zst` — 0.275 GiB | OK | PVE log says finished; no SHA-256 | Tunnel credentials inside guest; secret handling required |
| 104 | docker-stack | LXC | running, onboot | **Yes, with exclusions** | snapshot | 71.2 GiB of 98.1 GiB | `vzdump-lxc-104-2026_10_20-02_00_02.tar.zst` — 33.809 GiB | Last success 2026-10-20; 11-12 failed | PVE log says finished for 10-20; no SHA-256 | **Four bind mounts have `backup=0`**: downloads, backup, Immich drive, annotations. The archive is rootfs only. Docker named volumes are inside rootfs; bind-mounted application data is **not** covered |
| 200 | linuxmint | QEMU VM | running, onboot | **Yes** | snapshot | 130 GiB disk + 50 GiB disk; ~61 GiB actual | `vzdump-qemu-200-2026_11_10-02_30_02.vma.zst` — 28.043 GiB | Last success 2026-11-10; 11-12 failed | PVE log says finished for 11-10; no SHA-256 | Guest agent present (`qm agent 200 ping` exit 0); two disks; snapshot mode should be used |

Latest complete generation estimate from these archives: **≈66.6 GiB** — but CT104 is from 2026-10-20 and VM200 from 2026-11-10, so the next generation must be measured, not assumed.

## 2. Mount and exclusion detail

| Guest | Mount | Backed up by vzdump? | Consequence |
|---|---|---|---|
| CT102 | `/mnt/pve/immichdrive` → `/opt/immich/upload` | **Yes** (no `backup=0`) | Adds ~1.9 GiB; verify Immich DB consistency separately |
| CT104 | `/safedrive/downloads` | **No** (`backup=0`) | Download cache; not in archive |
| CT104 | `/safedrive/backup` | **No** (`backup=0`) | Backup target must never back itself up |
| CT104 | `/mnt/pve/immichdrive` | **No** (`backup=0`) | Immich data is on the host dataset; needs separate backup |
| CT104 | `/data/annotations` | **No** (`backup=0`) | 13.4 GiB of annotations; needs separate backup |

**CT104 is not a full application backup.** Its archive covers the container rootfs, including Docker metadata and named volumes under the rootfs. Bind-mounted data must be backed up by a separate dataset policy. This is a P0 documentation gap, not a reason to delete the archive.

## 3. Consistency assessment

| Guest | Consistency risk | Proposed method |
|---|---|---|
| CT100 AdGuard | Low; config files | snapshot; verify query/config after restore drill |
| CT101 Guacamole | Medium; database inside guest | snapshot; application-aware DB dump if the DB is actively written |
| CT102 Immich | **High**; PostgreSQL + uploads | snapshot rootfs + upload dataset; run `pg_dump` or stop DB for a consistent dump; do not claim a consistent photo backup from a crash-consistent snapshot alone |
| CT103 cloudflared | Low; config | snapshot; secret handling |
| CT104 Docker stack | **High**; 56 containers, databases, bind mounts | snapshot rootfs + per-database dump + named-volume inventory; bind mounts separately |
| VM200 Linux Mint | Medium; development data | snapshot with agent; verify two disks; quiesce if needed |

Snapshot mode is **crash-consistent**, not application-consistent. For databases, use a documented dump/quiesce step before or alongside the snapshot.

## 4. Host configuration coverage

| Path | Content | Include? | Handling |
|---|---|---|---|
| `/etc/pve` | Cluster, storage, backup jobs, notifications, firewall, user/token metadata | **Yes** | Encrypt the archive; `/etc/pve/priv` contains secrets |
| `/etc/network/interfaces` | Network | **Yes** | Plain config |
| `/etc/hosts`, `/etc/hostname` | Identity | **Yes** | Plain |
| `/etc/fstab` | Mounts | **Yes** | Plain |
| `/etc/pve/storage.cfg` | Storage definitions | **Yes** | Plain config |
| `/etc/pve/notifications.cfg` | PVE notification target | **Yes** | May contain endpoint data; no secrets printed |
| `/etc/pve/vzdump.cron` | Backup schedule | **Yes** | Plain |
| `/etc/pve/datacenter.cfg` | Datacenter settings | **Yes** | Plain |
| `/etc/systemd/system` custom units | Service definitions | **Yes** | Review for secrets |
| `/root` scripts | Operational scripts | Conditional | Include only documented scripts; review for credentials |
| Docker Compose files | Deployment config | **Yes, separately** | On CT104, not necessarily in host `/etc`; collect from the guest |

Do **not** copy live secrets into an unprotected directory. `/etc/pve/priv` and any credential files must be inside a GPG-encrypted archive; the encryption key must not live on the same disk as the backup.

## 5. Future eligible guests

The design must not hardcode the current six guests. A future discovery script should:

1. Enumerate `pct list` and `qm list`.
2. Exclude templates and explicitly opted-out guests.
3. Read each guest’s `backup=0` mounts and record exclusions.
4. Compute expected size from the latest verified archive and current disk usage.
5. Include new guests automatically in the next generation.
6. Require an explicit owner opt-out for any guest that must not be backed up.

This avoids silently missing a future CT/VM.

## 6. Verification status

Current archives are **not independently verified**. “PVE log says finished” means the job completed; it is not a SHA-256 or restore proof. Phase D must add:

- SHA-256 for every archive.
- `zstd -t` integrity test.
- `tar -tf` / `vma list` inventory.
- Manifest match against actual files.
- A restore drill in Phase F.

## 7. Inventory commands

`pct list`, `qm list`, `pct config`, `qm config`, `pct df`, `pct listsnapshot`, `qm listsnapshot`, `qm agent 200 ping`, `pvesm status`, `vzdump` task history.

No guest was stopped, started, suspended or written to.
