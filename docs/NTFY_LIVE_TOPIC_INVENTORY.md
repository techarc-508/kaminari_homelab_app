# ntfy live topic inventory

Date: 2026-11-11 UTC. This is an evidence-based inventory, not a claim that every topic on the server is known. ntfy does not expose a complete topic registry when auth is disabled.

## Verified topics

### `KAMINARI-FUJI-Homelab`

| Field | Evidence |
|---|---|
| Status | Verified live production topic |
| Producers | PVE webhook `kaminari-ntfy`; watchtower `WATCHTOWER_NOTIFICATION_URL`; `kaminari-docker-events` script |
| Consumers | Existing homelab dashboards/clients; exact subscriber list not enumerable without server-side inspection |
| ACL | None — authentication disabled |
| Sensitivity | High: Proxmox host/VM/container alerts, backup and container events |
| Proposed Kaminari use | **Read-only** subscription; never send test messages |
| Required permission | Read (currently anonymous) |
| Conflict/risk | Publishing test data here would pollute production alerts |

Evidence paths on the Docker LXC/PVE:

- `/etc/pve/notifications.cfg` → webhook target `kaminari-ntfy`, URL `http://<private-ntfy-lan>:8201/KAMINARI-FUJI-Homelab`
- `/opt/stack/utilities/docker-compose.ops.yml:131` → `WATCHTOWER_NOTIFICATION_URL: ntfy://ntfy.rplab.qzz.io/KAMINARI-FUJI-Homelab`
- `/opt/stack/kaminari-events/events.sh:7` and `/opt/stack/kaminari-fuji/tools/kaminari-events/events.sh:7` → `NTFY_URL="http://<private-ntfy-lan>:8201/KAMINARI-FUJI-Homelab"`

### `kaminari-onboarding-test`

| Field | Evidence |
|---|---|
| Status | Verified test topic; created for onboarding |
| Producers | Only the onboarding test |
| Consumers | The app/curl during onboarding |
| ACL | None — auth disabled |
| Sensitivity | Low; no production data |
| Proposed use | Safe publish and subscribe tests |
| Required permission | Read/write (currently anonymous) |

Verified live test: publish returned `event=message id=iC9Yb0cVwx0l`; read-back on the same public topic returned the same id.

## Unverified candidate names

These names appear only in the app's demo dataset, not in any server configuration or producer:

- `homelab-alerts`
- `iot-telemetry`
- `backups`
- `build-server`
- `security`

They are **not** confirmed live topics. Do not assume they exist. The app labels demo rows as sample data and does not use them for live configuration.

## Topic mapping and proposed app use

| Topic | Producer | Consumer | ACL | Sensitivity | Kaminari use | Permission | Risk |
|---|---|---|---|---|---|---|---|
| `KAMINARI-FUJI-Homelab` | PVE, watchtower, docker-events | homelab clients | none | high | read-only inbox | read | test publishes forbidden |
| `kaminari-onboarding-test` | onboarding only | app/curl | none | low | publish/subscribe test | read/write | none |

## Recommendations

- Keep `KAMINARI-FUJI-Homelab` as the real homelab inbox.
- Use `kaminari-onboarding-test` for every test.
- Do not create new production topics until auth is enabled and the owner approves ACLs.
- If auth is later enabled, give the app a non-admin user with read on `KAMINARI-FUJI-Homelab` and read/write on the test topic; do not grant admin.
