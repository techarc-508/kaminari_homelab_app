# Storage and Backup Monitoring Analysis

**Discovery date:** 2026-11-11  
**Status:** read-only. No backup, prune, scrub, restore or filesystem operation was executed.

---

## 1. Storage inventory

| Store | Type | Capacity | Used | Free | Health |
|---|---|---|---|---|---|
| `local-lvm` | LVM thin (`pve/data`) | 347.9 GB | 365 GB provisioned (over-provisioned) | ~16 GB VG free | Thin-pool warning |
| `fast-data` | LVM thin on NVMe | 238.2 GB | — | — | Present; not the backup target |
| `safedrive` | ZFS single-disk pool | 465.8 GB class | 142 GB | 307 GB | ONLINE; scrub clean |
| `safedrive/backup` | ZFS dataset | 150 GB | 125 GB | 25.1 GB | **84% used** |
| `safedrive/downloads` | ZFS dataset | 200 GB | 6.1 GB | 194 GB | Healthy |
| `safedrive/immichdrive` | ZFS dataset | 100 GB | 1.9 GB | 98.1 GB | Healthy |
| `backup-hdd` | Directory storage on `safedrive/backup` | 150 GB | 124.8 GB | 25.1 GB | **83.25% used** |
| `local` | Directory storage | — | — | — | Present; not audited for content |

**Important:** `backup-hdd` and `safedrive/backup` are the same physical pool/dataset. This is not a second independent copy. A single disk failure loses both the source snapshots and the backups.

`local-lvm` is over-provisioned: sum of thin volumes exceeds the thin pool, with only ~16 GB free in the volume group. The PVE backup log warns that thin-pool autoextend is not configured. Do not create more thin volumes without capacity planning.

---

## 2. ZFS and SMART

### ZFS

```text
pool: safedrive
state: ONLINE
scan: scrub repaired 0B in 00:22:29 with 0 errors on Sun Nov 8 00:46:31 2026
errors: No known data errors
```

The pool is a **single 465 GB HDD**, not a mirror or RAIDZ. A disk failure has no redundancy.

### SMART — `/dev/sda` (backup HDD)

| Attribute | Value | Interpretation |
|---|---|---|
| Overall health | PASSED | Not a failure prediction |
| Reallocated sectors | 0 | Good |
| Power-on hours | 29,106 | ~3.3 years; aging |
| Reported uncorrectable | **11,773** | **Elevated; investigate** |
| Command timeout | 11 / 11 / 129 | Some timeout history |
| Current pending sectors | 0 | Good at this snapshot |
| Offline uncorrectable | 0 | Good at this snapshot |
| UDMA CRC errors | 2 | Cable/controller history; low |
| Temperature | 38 °C | Normal |

**Do not claim the disk is failing.** SMART PASSED and the scrub is clean, but the uncorrectable counter and single-disk topology justify a replacement/backup plan before adding more data.

### NVMe

Both NVMe devices report SMART PASSED. Detailed wear/percentage-used values were not captured in the discovery output; capture them in implementation if the disks host VM data.

---

## 3. Backup jobs

| Job | Schedule | Target | Result |
|---|---|---|---|
| CT100/101/102/103 | Daily 01:00 / 01:30 | `backup-hdd` | Recent jobs **OK** |
| CT104 Docker stack | Daily 02:00 | `backup-hdd` | **FAILED** — `zstd: error 70: Write error: cannot write block: Disk quota exceeded` |
| VM200 Linux Mint | Daily 02:30 | `backup-hdd` | **FAILED** — `vma_queue_write: write error - Broken pipe` |
| Retention | `keep-last=2` on `backup-hdd` | — | Shallow; only two restore points |

### Evidence

- CT104 task log: `INFO: creating vzdump archive ...` then `ERROR: Backup of VM 104 failed ... exit code 70`; earlier line: `zstd: error 70 : Write error : cannot write block : Disk quota exceeded`.
- VM200 task log: `ERROR: vma_queue_write: write error - Broken pipe`; task status `job errors`.
- VM200 task also logs: `could not notify via target kaminari-ntfy: http status: 400`.
- `backup-hdd` content: 12 items; recent successful backups include CT100/101/102/103 and VM200 on 2026-11-09; CT104 has no successful recent archive in the sampled content.

**Inferred:** both failures are consistent with insufficient free space on the same backup dataset. The VM200 broken pipe is a generic write failure; it is not proven to be capacity-only. The CT104 failure is explicitly a disk-quota/write error.

---

## 4. What is not protected

- CT104 bind mounts (`downloads`, `backups`, `immichdrive`, `annotations`) have `backup=0` and are not in the CT104 container archive. They need independent backup or explicit acceptance.
- CT102 Immich uploads are on a ZFS mount; verify the upload dataset is included in a backup or replicated separately.
- VM200’s 50 GB ZFS disk is included in the VM backup only if the VM configuration includes it; the backup size suggests the main disk is included. Verify the secondary disk.
- There is no off-site copy visible in PVE storage configuration.
- Duplicati exists as a container but its jobs were not audited. It may provide off-site copies; this is unverified.
- No restore drill was performed.

---

## 5. Monitoring opportunities

### Tier 1 — must have

| ID | Signal | Detection | Trigger | Topic |
|---|---|---|---|---|
| STO-01 | ZFS pool not ONLINE / errors | `zpool status` or Scrutiny | any degraded/faulted state | `rplab-storage` |
| STO-02 | SMART failure or pending/reallocated growth | Scrutiny / smartctl | overall FAILED or critical attribute threshold | `rplab-storage` |
| STO-03 | Backup job failed | PVE task log or wrapper | any non-OK vzdump status | `rplab-backups` |
| STO-04 | Backup store capacity | PVE storage API / df | >85% warn, >92% critical | `rplab-backups` |
| STO-05 | Backup write/quota failure | task log pattern | `Disk quota exceeded`, `Broken pipe`, `No space` | `rplab-backups` |
| STO-06 | No successful backup within 26 h | Healthchecks dead-man or task parser | missing success | `rplab-backups` |

### Tier 2 — recommended

| ID | Signal | Trigger | Topic |
|---|---|---|---|
| STO-07 | `backup-hdd` >80% | warn; >90% critical | `rplab-backups` |
| STO-08 | `safedrive/backup` >80% | warn; >90% critical | `rplab-storage` |
| STO-09 | Thin pool free <15% | warn; <8% critical | `rplab-storage` |
| STO-10 | ZFS scrub errors or scrub overdue | any error; >35 days | `rplab-storage` |
| STO-11 | SMART temperature >50 °C | sustained 10 min | `rplab-storage` |
| STO-12 | External backup destination unavailable | Duplicati/remote check | `rplab-backups` |
| STO-13 | Backup verification failure | verification job | `rplab-backups` |
| STO-14 | Restore drill overdue | schedule check | `rplab-backups` |

### Tier 3 — advanced

| ID | Signal | Trigger |
|---|---|---|
| STO-15 | Capacity trend forecast | projected full <30 days |
| STO-16 | Snapshot growth anomaly | >20% week-over-week |
| STO-17 | Disk replacement forecast | SMART trend |
| STO-18 | Backup duration trend | >150% of baseline |

Do not send per-snapshot or per-GB metrics to ntfy. Keep trends in Beszel/Scrutiny/InfluxDB; send threshold/state transitions.

---

## 6. Recommended immediate remediation (requires owner approval, not executed)

1. Free space safely: identify old, already-pruned or unnecessary backups; do not delete the only good restore point.
2. Confirm at least one successful CT104 and VM200 backup after remediation.
3. Consider adding a second physical disk or an external/NAS target; a single-disk pool is not a backup strategy.
4. Turn on thin-pool autoextend protection or monitor free space explicitly.
5. Run a restore drill for one small CT and one VM; record the procedure and result.
6. Fix the PVE ntfy webhook 400 before relying on it for backup failure alerts.
7. Replace the aging HDD or move backups to a healthier target before increasing retention.
8. Do not enable ntfy persistence or add new data to this pool until capacity and backup health are restored.

---

## 7. Findings

| ID | Severity | Finding |
|---|---|---|
| STO-01 | **Critical** | CT104 and VM200 backups failing; backup store 83% full |
| STO-02 | **Critical** | Single-disk ZFS pool; no redundancy; no off-site copy verified |
| STO-03 | **High** | SMART uncorrectable counter 11,773; aging disk |
| STO-04 | **High** | `keep-last=2` is shallow; no restore drill evidence |
| STO-05 | **High** | CT104 bind mounts not included in container backup |
| STO-06 | **Medium** | Thin pool over-provisioned with ~16 GB VG free |
| STO-07 | **Medium** | PVE backup notification returns HTTP 400 |
| STO-08 | **Medium** | Duplicati off-site status unverified |

---

## 8. Evidence commands

```text
zpool status
zfs list
lsblk
df -hT
smartctl -H -A /dev/sda
pvesh get /nodes/pve/storage
pvesh get /nodes/pve/storage/backup-hdd/content
pvesh get /nodes/pve/tasks --limit 30
pvesh get /nodes/pve/tasks/<UPID>/log
```

No backup was run, deleted, pruned or restored.
