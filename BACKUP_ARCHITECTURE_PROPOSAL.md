# Backup Architecture Proposal

**Phase:** A — proposal only
**Date:** 2026-11-12
**Status:** **NOT IMPLEMENTED.** No device was formatted, no LV was created, no storage definition was changed, no backup was run, and no archive was deleted.

## 1. Target architecture

```text
PVE guests ──vzdump──> SSD primary ──verify──> HDD secondary ──future──> encrypted off-site
                         │                        │
                         └──── manifests, logs, checksums, restore drills ────┘
```

| Tier | Device | Role | Retention | Independence |
|---|---|---|---|---|
| Primary | Existing NVMe `XPG GAMMIX S11 Pro` (`nvme0n1`) | Fast rolling generations and first restore source | 2 verified daily generations (recommended); 3 only with capacity exception | Local |
| Secondary | Existing HDD `ST500DM002` (`sda`) | Copy of verified SSD generations | 2 verified generations | **Same physical server; not disaster recovery** |
| Off-site | None yet | True disaster recovery | TBD | Future |

The HDD must never receive a newly created generation before that generation is complete and verified on the SSD.

## 2. Exact layout

### SSD primary

```text
/mnt/kf-backup-ssd/
  kaminari-backups/
    generations/
      G-2026-11-12/
        vzdump-lxc-100-...tar.zst
        vzdump-lxc-101-...tar.zst
        vzdump-lxc-102-...tar.zst
        vzdump-lxc-103-...tar.zst
        vzdump-lxc-104-...tar.zst
        vzdump-qemu-200-...vma.zst
        host-config-2026-11-12.tar.gz.gpg
        generation.json
        SHA256SUMS
        verification.txt
    manifests/
    logs/
    verification/
    staging/
    README.txt
```

### HDD secondary

```text
/safedrive/backup/kaminari-backups/
  generations/
    G-2026-11-13/
    G-2026-11-14/
  manifests/
  logs/
  verification/
```

Do not place the SSD generation inside the existing `/safedrive/backup/vzdump` directory. The HDD target should be a new subdirectory on the existing ZFS dataset, not a new pool or dataset, unless the owner approves a quota change.

### Proposed PVE storage entries

| Name | Type | Path | Content | Notes |
|---|---|---|---|---|
| `kf-backup-ssd` | `dir` | `/mnt/kf-backup-ssd/kaminari-backups` | `backup` | New directory storage after the LV is mounted |
| `kf-backup-hdd` | `dir` | `/safedrive/backup/kaminari-backups` | `backup` | New subdirectory; no new dataset |

`vzdump` can write directly to a temporary generation directory if the installed `vzdump` supports the chosen dump-directory mode. The exact flag must be verified in Phase B; otherwise, write to a staging directory and move completed archives into the generation directory. **Do not rely on PVE’s automatic `prune-backups` for this layout**; the custom retention script owns deletion, and PVE storage pruning must be disabled or set to `keep-all=1` to prevent a second, untracked deletion path.

## 3. SSD carve-out

The SSD is already an LVM PV and an active PVE `lvmthin` store (`fast-data-ssd/data`, 238.24 GiB, 0 % data used). The non-destructive proposal is:

1. Create a thin LV from `fast-data-ssd/data`, e.g. `kf-backup` at **200–210 GiB** (recommended) or up to 238 GiB with an explicit reserve exception.
2. Format **only that new LV** with ext4; do not touch `nvme0n1` partition tables, the existing PV, the thin pool, or `fast-data`.
3. Mount it at `/mnt/kf-backup-ssd`.
4. Add the mount to `/etc/fstab` with `nofail` and verify a reboot in Phase C.
5. Add the PVE `dir` storage with `content backup`.
6. Verify `pvesm status`, mount persistence and free space.

**Risks:** the thin LV consumes pool capacity and reduces space available to future `fast-data` guests. A 200–210 GiB LV leaves 28–38 GiB in the pool, which is not much for future guest disks. This must be an owner decision. An alternative is to repartition the SSD, which is more invasive and is **not recommended**.

## 4. Generation state machine

```text
PLANNED
  -> STAGING          (vzdump running)
  -> SOURCE_VERIFIED  (all archives, checksums, manifest)
  -> REPLICATING      (copy to HDD)
  -> BOTH_VERIFIED    (independent HDD checksums)
  -> ACTIVE           (current restore point)
  -> PRUNED           (only after a newer generation is BOTH_VERIFIED)

Any failure: FAILED -> preserve all existing ACTIVE/BOTH_VERIFIED generations; no prune.
```

Rules:

- A generation is never `ACTIVE` until every expected guest has a verified archive or an explicitly recorded, owner-approved exclusion.
- A generation is never copied to HDD until `SOURCE_VERIFIED`.
- A generation is never pruned from SSD until a newer generation is `BOTH_VERIFIED`.
- A generation is never pruned from HDD until a newer generation is independently verified **and** the retention window says so.
- A failed or partial generation remains in `staging/` for diagnosis; it is never counted as a restore point.

## 5. Daily execution order

### Preflight

1. Acquire an exclusive lock.
2. Read the previous `generation.json`; estimate the next size at `previous × 1.10` unless overridden.
3. Calculate free space on SSD and HDD.
4. Apply the capacity gate: SSD `≥ estimate × 1.20 + 20 GiB`; HDD `≥ estimate × 1.20 + 10 GiB`.
5. Verify mount points, PVE storage status, `zpool health`, and that no other `vzdump` is running.
6. If any check fails, stop and notify. Do not delete anything.

### Create

7. `vzdump` every eligible guest, one at a time, snapshot mode, zstd, into `generations/G-YYYY-MM-DD/`.
8. Record start/end, exit code and log for each guest.
9. Capture host configuration into an encrypted archive.
10. Write `generation.json` and `SHA256SUMS`.

### Verify source

11. Non-zero size for every archive.
12. `zstd -t` for `.zst`/`.vma.zst`; `tar -tf` for LXC; `vma list` or the supported equivalent for VM archives.
13. Recompute SHA-256 and compare with `SHA256SUMS`.
14. Confirm every expected guest appears exactly once; no duplicate IDs; no missing guest.
15. Only then mark `SOURCE_VERIFIED`.

### Replicate

16. Create `HDD/generations/G-...` in a staging path.
17. Copy archives, manifest, checksums and host config.
18. Recompute SHA-256 **on the HDD copy**; compare independently.
19. Verify archive format again on the HDD copy.
20. Only then mark `BOTH_VERIFIED` and update the `CURRENT` pointer atomically.

### Prune

21. Determine the retention window using **verified generation IDs**, never a wildcard.
22. Delete the oldest SSD generation only if a newer generation is `BOTH_VERIFIED` and SSD retention is exceeded.
23. Delete the oldest HDD generation only after the replacement is `BOTH_VERIFIED`.
24. Log every deletion decision and result.
25. If any copy or verification fails, skip all pruning and alert.

## 6. Generation manifest

`generation.json` must contain:

```json
{
  "schema": "kf.backup.generation.v1",
  "generation_id": "G-2026-11-12",
  "created_at": "2026-11-12T01:00:00+01:00",
  "host": "pve",
  "pve_version": "9.2.11",
  "status": "BOTH_VERIFIED",
  "ssd_path": "/mnt/kf-backup-ssd/kaminari-backups/generations/G-2026-11-12",
  "hdd_path": "/safedrive/backup/kaminari-backups/generations/G-2026-11-12",
  "guests": [
    {
      "vmid": 104,
      "name": "docker-stack",
      "type": "lxc",
      "archive": "vzdump-lxc-104-...tar.zst",
      "size_bytes": 36301750443,
      "sha256": "...",
      "started_at": "...",
      "finished_at": "...",
      "status": "verified",
      "excluded_mounts": ["/safedrive/downloads", "/safedrive/backup", "/safedrive/immichdrive", "/data/annotations"]
    }
  ],
  "host_config": {
    "archive": "host-config-2026-11-12.tar.gz.gpg",
    "sha256": "...",
    "encrypted": true
  },
  "totals": {
    "guest_count": 6,
    "archive_bytes": 71500000000,
    "expected_guests": 6,
    "verified_guests": 6
  }
}
```

No secrets in the manifest. Host configuration is referenced, not inlined.

## 7. Guest coverage and consistency

| Guest | Method | Exclusion/consistency action |
|---|---|---|
| CT100 | snapshot | None |
| CT101 | snapshot | Application-aware DB dump if the guest database is actively written |
| CT102 | snapshot | Add a PostgreSQL/Immich database dump; the bind-mounted upload dataset is included in the CT archive today, but verify consistency |
| CT103 | snapshot | Encrypt host config; guest secret files stay inside the encrypted archive |
| CT104 | snapshot | **Rootfs only**; four `backup=0` mounts must be backed up separately. Inventory Docker named volumes and bind mounts; dump databases |
| VM200 | snapshot with agent | Two disks; quiesce or document crash consistency; verify agent |

A crash-consistent snapshot is not the same as an application-consistent database backup. Immich and the Docker stack must use documented application-aware preparation.

## 8. Host configuration backup

Collect:

- `/etc/pve` (including `priv`, firewall, storage, jobs, notifications, user/token metadata)
- `/etc/network`, `/etc/hosts`, `/etc/hostname`, `/etc/fstab`
- `/etc/systemd/system` custom units
- `/etc/pve/vzdump.cron`/job config
- Documented operational scripts only
- Docker Compose/deployment config from CT104, collected separately

Create `host-config-YYYY-MM-DD.tar.gz`, encrypt with GPG (or age) using a key held **off** the backup devices, and include only the encrypted file in the generation. Record the key fingerprint and recovery instructions outside the repository. Never store the passphrase on the SSD or HDD.

## 9. Retention

| Target | Recommended | Minimum safe | Notes |
|---|---|---|---|
| SSD | **2 daily generations** | 2 | 3 not safe with 20 % reserve at current size |
| HDD | **2 daily generations** | 2 | Requires a 220–240 GiB quota for safe copy order |
| Off-site | Future | 1+ | Encrypted, versioned, immutable if available |

No grandfather-father-son scheme is claimed until capacity proves it. Daily-only is the honest Phase A baseline.

## 10. Restore model

- **RPO:** 24 hours for daily backups. Any shorter RPO requires more frequent runs and more capacity.
- **RTO:** not measured. Targets to prove in Phase F: 1 hour for the small CTs, 4 hours for CT104, 4 hours for VM200. These are targets, not claims.
- Restore only to a new VMID or an isolated test bridge; never overwrite production.
- Test one LXC (CT100 or CT103), one VM (VM200), and the host configuration procedure.
- Record actual restore duration, commands and verification evidence.
- The HDD copy must be used in at least one drill to prove the secondary is usable.

## 11. Notifications

Use the approved `kf-` topics after they exist:

| Event | Topic |
|---|---|
| Backup started/completed, guest failure | `kf-backups` |
| SSD/HDD capacity and SMART/pool health | `kf-storage` |
| Host/node issue | `kf-pve-host` |
| CT/VM lifecycle issue | `kf-pve-ct`, `kf-pve-vm` |
| Daily report | `kf-digest` |

No new ntfy server. Until the `kf-` topics exist, use `kaminari-onboarding-test` for harmless tests only; never publish test messages to production alert topics.

## 12. Future off-site design

Requirements, not implementation:

- Client-side encryption before upload; the cloud provider must not hold plaintext.
- Dedicated service identity with least privilege; credentials in a secret store, never in the repo.
- Versioned/immutable bucket where available; object lock if supported.
- Upload only `BOTH_VERIFIED` generations.
- SHA-256 verified after upload and on periodic download.
- Retention independent of local disks; expire only after a newer off-site generation is verified.
- Restore procedure tested from the cloud copy.
- Credential expiry/rotation and a documented recovery when credentials are unavailable.
- No claim of off-site protection until an actual upload and restore test succeeds.

## 13. Explicit non-goals in Phase A

No LV creation, mkfs, mount, PVE storage edit, schedule change, backup run, archive deletion, snapshot deletion, quota change, cloud upload or restore. Phase A is evidence and design only.

---

# Revision 2 — Owner-directed final architecture (2026-11-12)

**This revision supersedes the SSD 3-generation and HDD 2-generation design above.** Final target: **SSD dedicated filesystem with two verified generations; HDD one latest verified generation; all 12 historical archives and 12 logs preserved until separately authorized.**

## R2.1 SSD conversion — verified dependency state

Read-only evidence:

- `pvs` → `/dev/nvme0n1` is PV `fast-data-ssd`, VG `fast-data-ssd`.
- `lvs -a` → thin pool `data` (238.24 GiB), `data_tdata`, `data_tmeta`; **no thin volumes**.
- `pvesm list fast-data` → **no volumes**.
- `grep -R fast-data /etc/pve/nodes/pve/{lxc,qemu-server}` → no guest reference.
- `pvesm status --storage fast-data` → active, type `lvmthin`, content `images,rootdir`.
- No guest disk or ISO depends on it.

**Conclusion:** the thin pool is empty and unreferenced, but it is still an active PVE storage. A zero-data pool is not proof that reinitialization is risk-free until the storage definition and LVM metadata are removed in order.

## R2.2 Exact conversion sequence (future, not executed)

Preconditions:

1. Owner approves destruction of `fast-data` and the SSD LVM layout.
2. All PVE storage and guest configs backed up: `/etc/pve/storage.cfg`, `/etc/pve/nodes/pve/**/*.conf`, `/etc/fstab`.
3. No backup job, replication job or manual task targets `fast-data`.
4. Maintenance window; no guest on `fast-data` (confirmed none).

Proposed sequence:

```bash
# 1. Remove the PVE storage definition only (does not delete LVs).
pvesm remove fast-data

# 2. Remove the empty thin pool LV.
lvremove fast-data-ssd/data

# 3. Remove the VG and PV metadata.
vgremove fast-data-ssd
pvremove /dev/nvme0n1

# 4. Erase old filesystem/LVM signatures (destructive; approval gate).
wipefs -a /dev/nvme0n1

# 5. Create the dedicated filesystem.
mkfs.ext4 -L KAMINARI-BACKUP /dev/nvme0n1
```

Mount plan:

```fstab
UUID=<new-uuid> /mnt/kaminari-backup ext4 defaults,noatime,nofail,x-systemd.device-timeout=10 0 2
```

- `mkdir -p /mnt/kaminari-backup`
- `chmod 700 /mnt/kaminari-backup`
- `systemctl daemon-reload`; `mount -a`; verify with `findmnt`.
- Reboot test before production use.

**PVE storage registration:** not required for the generation-directory design. `vzdump --dumpdir /mnt/kaminari-backup/generations/G-YYYY-MM-DD` writes directly to the generation directory. Do **not** register the same directory as a PVE `dir` storage with automatic pruning; that would conflict with manifest-based retention. If GUI ad-hoc backups are wanted later, register a separate directory and document that it is outside the generation pipeline.

## R2.3 Rollback

| Failure point | Rollback |
|---|---|
| `pvesm remove` succeeds but LVM removal fails | Re-add `fast-data` with the original `storage.cfg` backup and re-activate; LVs still intact |
| `lvremove`/`vgremove` fails | Stop; do not wipe; restore storage config; LVM metadata remains |
| `wipefs` or `mkfs` fails | Device may be partially erased; do not use; restore from configuration backup; no guest data was on it |
| Mount fails after reboot | Boot without `nofail` blocking; correct UUID/fstab; backups fail closed |
| Any guest unexpectedly references `fast-data` | Abort before `pvesm remove`; migrate that disk first with explicit approval |

## R2.4 HDD quota transfer — exact model

Current:

| Dataset | Quota | Used | Available | Reservation |
|---|---:|---:|---:|---:|
| `safedrive/downloads` | 200 GiB | 6.04 GiB | 193.96 GiB | none |
| `safedrive/backup` | 150 GiB | 124.87 GiB | 25.1 GiB | none |
| `safedrive/immichdrive` | 100 GiB | 1.90 GiB | 98.1 GiB | none |
| pool | 464 GiB | ~? | 307 GiB | — |

Proposed conversion, **not executed**:

```bash
zfs set quota=150G safedrive/downloads
zfs set quota=200G safedrive/backup
```

Rationale: quotas limit maximum usage, not physical allocation. The downloads dataset keeps 143.96 GiB headroom; backup gains 50 GiB. No data is deleted or moved.

**Capacity warning:** with all 12 historical archives retained (124.802 GiB), a 200 GiB backup quota leaves only **8.6 GiB** for the latest generation at current size. A 66.6 GiB generation leaves 4.3 % free; a 73.3 GiB generation leaves 0.95 %; 78 GiB fails. This is a **tight interim** until the historical archives are separately approved for retirement. The safe alternative is a 220–240 GiB quota, but that exceeds the owner's requested +50 GB unless more is recovered from downloads.

## R2.5 Revised daily rotation (SSD 2 / HDD 1)

State machine:

```
G_n created on SSD
  -> all archives exist, zstd -t, SHA-256, manifest
  -> G_n verified
  -> copy G_n to HDD /generations/G_n
  -> independent SHA-256 on HDD
  -> HDD manifest verified
  -> only then prune SSD G_(n-2)
  -> only after owner authorization, prune old HDD historical archive outside policy
```

Rules:

1. Never delete the last verified SSD generation.
2. Never delete an HDD generation until the replacement is verified.
3. Keep the 12 historical archives and 12 logs untouched until separate authorization.
4. If the new generation fails, keep both old generations and stop.
5. If HDD copy fails, keep the SSD generation and both old SSD generations; do not prune.
6. Refuse to start if SSD free < estimated generation × 1.20 or HDD free < generation × 1.10.
7. Use explicit generation IDs, not wildcards.
8. A rootfs-only CT104 archive is not complete coverage.

## R2.6 Coverage unchanged

All six guests are in scope, plus:

- CT104 excluded bind mounts: downloads (cache), backup (not backed up by itself), Immich drive, annotations.
- Immich uploads + PostgreSQL dump.
- Docker named volumes + per-database dumps.
- Essential PVE host config (`/etc/pve`, network, storage, firewall, notifications, backup jobs, scripts/units).
- Application-consistent preparation for Immich and Docker.

A rootfs snapshot alone is **not** a complete recovery point.
