# Backup Risk Register

**Phase:** A — read-only
**Date:** 2026-11-12
**Scale:** Critical / High / Medium / Low. Likelihood is not scored where evidence is insufficient.

## 1. Highest-priority risks

| ID | Risk | Severity | Evidence | Required response |
|---|---|---|---|---|
| BR-01 | **Three SSD generations do not fit with a 20 % reserve.** 3 × 66.6 GiB = 199.8 GiB on a 235 GiB usable filesystem, leaving ~15 % before growth. | **High** | `BACKUP_CAPACITY_MODEL.md` §2 | Accept 2 generations or approve a documented reserve exception with a growth cap; do not silently reduce retention. |
| BR-02 | **HDD 150 GiB quota cannot stage two old generations plus a new one.** 3 × 66.6 = 199.8 GiB. | **High** | `pvesm status`; capacity §3 | Raise quota to 220–240 GiB, or accept a one-generation transient copy state. |
| BR-03 | **HDD is the same physical pool as downloads, Immich and a VM disk.** A disk failure loses both “copies” and unrelated data. | **Critical** | `zpool status`, `zfs list` | Treat HDD as secondary only; add a verified off-site copy; do not claim disaster recovery. |
| BR-04 | **HDD is aging and historically error-prone.** 29,127 h, `Reported_Uncorrect` 11,773, `Raw_Read_Error_Rate` 178,081,832, CRC 2. | **High** | SMART attributes | Replace or demote to secondary; run regular SMART/scrub checks; keep SSD as first restore source. |
| BR-05 | **No verified off-site backup.** | **Critical** | Environment inventory | Add encrypted off-site target and test restore before calling the system disaster-ready. |
| BR-06 | **No archive has a checksum or restore proof.** “PVE log says finished” is not verification. | **High** | Existing archives; manifest | Implement SHA-256, format test, manifest match, and restore drills. |
| BR-07 | **CT104 archive excludes four bind-mounted datasets**, including Immich and 13.4 GiB annotations. | **Critical** | `pct config 104` (`backup=0`) | Separate dataset/application backup; do not call CT104 archive a full application backup. |
| BR-08 | **Immich and Docker databases are crash-consistent only.** | **High** | `BACKUP_GUEST_INVENTORY.md` §3 | Application-aware dumps/quiesce; test restore. |
| BR-09 | **VM200 backup uses snapshot mode with two disks**; the second disk is a ZFS zvol on the same HDD pool. | **Medium** | `qm config 200` | Verify both disks in the archive; test restore; ensure snapshot space. |
| BR-10 | **SSD thin LV carve-out consumes the empty `fast-data` pool and reduces future guest capacity.** | **Medium** | `pvs`/`lvs` (0 GiB VG free; 238.24 GiB thin pool) | Approve the exact LV size and record the future-guest trade-off. |
| BR-11 | **SSD namespace utilization is 192 GB despite 0 % thin-pool use.** | **Medium** | `smartctl -i` | Do not format; investigate TRIM/prior allocation; benchmark before relying on full capacity. |
| BR-12 | **CT104 filesystem is 87.27 % used (about 12.7 GiB free).** A backup-time snapshot or live growth can pressure it. | **Medium** | `lvs` data percent | Monitor free space; consider cleanup before backups; do not delete application data. |
| BR-13 | **`local-lvm` thin pool has 192.8 GiB free but is shared by all guest snapshots.** | **Medium** | `lves`/`pvesm` | Monitor snapshot COW; abort if pool usage crosses a threshold. |
| BR-14 | **No automatic prune may run outside the script.** PVE `prune-backups` and the script could both delete. | **High** | `storage.cfg` `keep-last=2` | Disable/neutralize PVE storage prune; only the validated script deletes, with dry-run and logs. |
| BR-15 | **The current backup schedule writes directly to `backup-hdd`**, which is nearly full and has no generation concept. | **High** | `vzdump.cron` | Replace only in Phase H after Phase D/E verification. |
| BR-16 | **Backup failure is only visible if the PVE notification path works.** The webhook 400 fix has not been exercised by a real post-fix backup event. | **High** | `PVE_NOTIFICATION_REPAIR_REPORT.md` | Run a safe test and confirm a real successful/failed job reaches ntfy. |
| BR-17 | **NTFY notification topics `kf-*` do not exist yet.** | **Medium** | Freebuff Phase 0; live inventory | Keep notifications on a safe test topic until the approved catalogue is created. |
| BR-18 | **Host configuration contains secrets** (`/etc/pve/priv`, tunnel credentials, guest configs). | **High** | `/etc/pve` contents | Encrypt with GPG/age; keep the key off the backup devices; never commit. |
| BR-19 | **Restore could overwrite a running guest if a VMID is reused.** | **High** | Procedure risk | Restore to a new VMID/isolated bridge; require explicit confirmation; never overwrite production. |
| BR-20 | **Backup window may overlap with production I/O and snapshots.** CT104 and VM200 are the largest jobs. | **Medium** | Current cron 01:00–02:30 | Measure duration in Phase D; run sequentially; cap concurrency. |
| BR-21 | **Disk space can be consumed by a failed partial generation.** | **Medium** | No incomplete artifacts today | Keep staging bounded; never count partials; clean only after owner-approved diagnosis. |
| BR-22 | **A future guest is silently omitted.** | **Medium** | Current six guests only | Enumerate guests dynamically; require an explicit opt-out; fail if an expected guest is missing. |
| BR-23 | **The HDD quota increase is on the same pool**, so it does not add redundancy. | **High** | `zfs list` | Do not present quota expansion as a resilience improvement; it only enables safe staging. |
| BR-24 | **Cloud credentials may expire or be revoked.** | **Medium** | Future design | Document rotation and a recovery path; never make cloud the only copy. |
| BR-25 | **No measured RTO exists.** | **Medium** | No restore drill | Measure in Phase F; do not publish a target as fact. |

## 2. Accepted operational constraints

| Constraint | Why accepted for now |
|---|---|
| Same physical server for SSD and HDD | No new VM/CT/disk is authorized; off-site is the real fix. |
| Daily RPO | Capacity and current backup duration support daily, not hourly. |
| Snapshot mode | Minimizes downtime; not application-consistent. Application dumps are added separately. |
| Single PVE node | No cluster/HA target exists; host config backup covers rebuild. |
| HDD remains online | It holds downloads/Immich/VM data; do not remove it. |

## 3. Owner decisions needed

1. Accept **2 SSD generations** or approve a **3-generation exception** with a 15 % free floor and growth alarm.
2. Approve **220–240 GiB HDD quota** or accept the **transient one-generation** copy state.
3. Approve **SSD carve-out** (new thin LV, ext4, PVE dir storage) and its size.
4. Approve the **application-aware backup** requirement for Immich and Docker.
5. Approve the **host configuration encryption/key custody** model.
6. Approve the **restore-drill target** (new VMID/isolated bridge) before Phase F.

No risk in this register has been mitigated by a production change in Phase A.

---

# Revision 2 — Architecture revision risks (2026-11-12)

| ID | Risk | Severity | Evidence | Mitigation |
|---|---|---|---|---|
| BR-26 | SSD reinitialization destroys the `fast-data` LVM layout and cannot be undone | **High** | `pvs`/`lvs` show dedicated PV/VG/thin pool | Backup `/etc/pve`; verify zero guest refs; explicit approval; staged `pvesm remove` → `lvremove` → `vgremove` → `pvremove` → `wipefs` |
| BR-27 | A hidden guest or job reference to `fast-data` is missed | **High** | `pvesm list` empty; no config grep hit | Re-run dependency scan immediately before change; abort on any hit |
| BR-28 | `pvesm remove` is not a data-destroying operation; operators may assume it wiped the pool | Medium | CLI semantics | Document that LVs remain; remove them explicitly in order |
| BR-29 | Wrong SSD is formatted (root/guest disk) | **Critical** | Two NVMe devices present | Use `/dev/disk/by-id/nvme-XPG...` not `/dev/nvme0n1`; confirm serial; two-person check |
| BR-30 | HDD quota transfer leaves backup dataset too tight with historical archives | **High** | 124.802 GiB history + 66.6 GiB = 191.4 GiB of 200 GiB | 220–240 GiB quota or owner approval to retire history; hard generation-size gate |
| BR-31 | Reducing downloads quota harms downloads growth | Medium | 200 → 150 GiB; 6.04 GiB used | 143.96 GiB headroom remains; monitor; revert with `zfs set quota=200G` |
| BR-32 | ZFS quota is mistaken for allocated space | Medium | No reservations; pool free 307 GiB | Document quota vs reservation vs used; do not claim physical reclamation |
| BR-33 | A 238 GiB SSD still cannot hold three generations safely | Medium | 3 × 66.6 = 199.8 GiB = 85% | Final target is two generations; no three-generation claim |
| BR-34 | SSH/console access lost during storage conversion | High | PVE config edits | Keep SSH session; back up config; rollback plan; no reboot until mount verified |
| BR-35 | HDD single disk and 11,773 uncorrectable events | **Critical** | SMART | SSD primary; HDD secondary only; off-site still required |
| BR-36 | Old archives and logs retained consume HDD headroom | High | 124.802 GiB history | Do not delete without exact authorization; 200 GiB is interim |
| BR-37 | PVE automatic `prune-backups` conflicts with generation retention | Medium | Storage/job config | Orchester uses `--dumpdir`; do not register same path as auto-pruning `dir` storage |
| BR-38 | CT104 rootfs-only archive reported as full recovery | High | Four `backup=0` mounts | Separate dataset/volume backup; coverage matrix |
