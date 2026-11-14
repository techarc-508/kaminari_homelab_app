# Proxmox Node, CT and VM Inventory

**Discovery date:** 2026-11-11  
**Scope:** PVE host, guests, storage and guest-level monitoring potential. No guest filesystem or application content was inspected.

---

## 1. Node

| Property | Value |
|---|---|
| PVE version | 9.2.11 |
| Node | `pve` (single node; no cluster) |
| Uptime | 10 days at discovery |
| CPU | AMD Ryzen 5 5600GT, 6 cores / 12 threads |
| RAM | 30 GiB total; ~22 GiB used; ~8.4 GiB available |
| Load | ~0.82 / 0.73 / 0.88 |
| Root filesystem | 94 GB ext4, 15% used |
| PVE firewall | **disabled/running** |
| Existing host agents | `smartmontools`; Tailscale; no Prometheus/node_exporter/Netdata |
| Guest agent | VM200 configured `agent:1`; live ping not confirmed |

---

## 2. Guests

| ID | Name | Type | Cores | RAM | Root disk | State | On boot | Role |
|---|---|---|---|---|---|---|---|---|
| 100 | `adguard` | LXC | 2 | 512 MB | 8 GB | running | yes | DNS/ad blocking |
| 101 | `apache-guacamole` | LXC | 2 | 2 GB | 4 GB | running | yes | Remote access gateway |
| 102 | `immich` | LXC | 4 | 3 GB | 19 GB (+100 GB ZFS mount) | running | yes | Photo library |
| 103 | `cloudflared` | LXC | 1 | 512 MB | 4 GB | running | yes | Cloudflare Tunnel |
| 104 | `docker-stack` | LXC | 6 | 15 GB | 100 GB (77% used) | running | yes | 50+ Docker services, including ntfy |
| 200 | `linuxmint` | VM | 4 | 8 GB | 130 GB + 50 GB ZFS | running | yes | Development workstation |

**Observations:**

- CT104 is the highest-risk guest: 6 cores, 15 GB RAM, 100 GB disk at **77%**, the Docker data/build cache consumes space, and its backup is currently failing.
- CT102 Immich has a separate ZFS mount for uploads; back up the database and the upload dataset consistently.
- CT100 AdGuard is a single point of failure for LAN DNS; its outage can look like a WAN outage.
- CT103 is the only public ingress path for the websites and ntfy; its outage makes all public hostnames unavailable while the LAN stays up.
- VM200 is the development VM; it has a 50 GB ZFS disk and is currently the largest backup failure risk.

---

## 3. Guest configuration notes

- All guests have `onboot=1`; a host reboot should restore them automatically, but no reboot test was performed.
- All are on `vmbr0`; private addresses are redacted from this report.
- CT104 has four bind mounts: downloads, backups, Immich drive and annotations. These are **not included** in its container backup (`backup=0`), so the container backup does not protect the mounted data.
- CT102 has the Immich upload directory mounted from ZFS; verify that the backup covers the mount or that a separate upload backup exists.
- VM200 has `agent:1`; `qm agent 200 ping` returned no output in discovery, so guest-agent liveness is **not confirmed**.
- No guest-level monitoring agent was found or installed during discovery.

---

## 4. What PVE can monitor directly

| Signal | Source | Available without guest agent |
|---|---|---|
| Guest running/stopped | PVE API / `pct list` / `qm list` | Yes |
| Guest CPU/RAM/disk usage | PVE RRD / API | Yes |
| Guest uptime | PVE API | Yes |
| Guest disk I/O | PVE RRD | Yes |
| Guest network I/O | PVE RRD | Yes |
| Guest status changes | PVE task/event log | Yes |
| Backup task success/failure | PVE task log | Yes |
| Storage capacity | PVE storage API | Yes |
| SMART | `smartctl` / Scrutiny | Host/disk level |
| Application health inside guest | HTTP probe / guest agent | Needs service-level check |
| OOM inside guest | guest logs / cgroup | Needs guest-level access |

---

## 5. Monitoring recommendations by guest

| Guest | Tier 1 | Tier 2 | Tier 3 | Topic |
|---|---|---|---|---|
| CT100 AdGuard | process/port down; DNS resolution failure | query latency; upstream failure; restart | query volume trend | `rplab-services` |
| CT101 Guacamole | web/API down | auth failures; session errors; restart | usage trend | `rplab-services` |
| CT102 Immich | service down; upload path full | DB/queue errors; backup consistency | library growth | `rplab-services` |
| CT103 cloudflared | tunnel disconnected | restart; tunnel latency | route changes | `rplab-network` |
| CT104 Docker | disk >80%; Docker daemon down | container unhealthy; OOM; restart loops | image/build-cache growth | `rplab-pve-ct` + `rplab-services` |
| VM200 Linux Mint | VM down | disk >80%; memory pressure | dev-service health | `rplab-pve-vm` |
| Host `pve` | node down; storage degraded | CPU/RAM pressure; SMART; thin pool | trends | `rplab-pve-host` |

Do not create one topic per guest. Use `rplab-pve-ct` for LXC lifecycle, `rplab-pve-vm` for VM lifecycle, and `rplab-services` for application health.

---

## 6. Guest lifecycle and recovery

- All guests are `onboot=1`; automatic start is configured, but no reboot test was performed.
- A guest stop/start should produce a state-transition alert, not a stream of metrics.
- A guest restart loop should alert once and then suppress repeats for a cooldown window.
- A recovery message should include how long the guest was down.
- CT104’s backup failure must be treated as a **data-protection incident**, not just a guest alert.

---

## 7. Unknowns

- Whether the PVE host has a UPS/NUT integration (no NUT service found).
- Whether the router provides WAN failover.
- Whether the host has IPMI/iDRAC or out-of-band management (not discovered).
- Whether VM200’s guest agent is actually running.
- Whether CT104’s Docker data/build cache is safe to prune; do not prune without owner approval.
- Whether the existing `backup-hdd` retention policy is sufficient; `keep-last=2` is shallow and the store is nearly full.

---

## 8. Findings

| ID | Severity | Finding |
|---|---|---|
| PVE-01 | **Critical** | Backup storage is nearly full and CT104/VM200 backups are failing |
| PVE-02 | **High** | PVE firewall disabled; public services rely on Cloudflare/tunnel and host network isolation |
| PVE-03 | **High** | CT104 is a single Docker host for 50+ services, including ntfy and the website; disk at 77% |
| PVE-04 | **High** | CT103 is a single ingress path; its failure removes all public hostnames |
| PVE-05 | **Medium** | CT100 AdGuard is a single DNS dependency; outage mimics WAN failure |
| PVE-06 | **Medium** | Guest-agent liveness unconfirmed; no guest-level monitoring agent |
| PVE-07 | **Medium** | No UPS/power monitoring evidence |
| PVE-08 | **Low** | No cluster; no HA; single node is a single point of failure |

---

## 9. Evidence commands

```text
pveversion
pct list
qm list
pct config <id>
qm config 200
pvesh get /nodes/pve/status
pvesh get /nodes/pve/tasks --limit 30
pvesh get /nodes/pve/storage
zpool status
df -hT
smartctl -H -A /dev/sda
```

Private addresses, tokens and guest application data are redacted.
