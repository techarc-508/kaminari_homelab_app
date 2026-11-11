# ntfy live topology

```
Phone / Mint VM / PVE
        │
        │ HTTPS  https://ntfy.rplab.qzz.io
        ▼
Cloudflare DNS + TLS
        │
        ▼
cloudflared on CT 103
        │  private tunnel
        ▼
CT 104 (docker-stack)
  Docker network 172.19.0.0/16
  container ntfy → 172.19.0.4:80
  published LAN: 192.168.0.104:8201
        │
        ▼
ntfy 2.28.0 (defaults, auth disabled)
  volume utilities_ntfy_data → /var/lib/ntfy
        │
        ├── PVE webhook (kaminari-ntfy) → KAMINARI-FUJI-Homelab
        ├── watchtower → KAMINARI-FUJI-Homelab
        └── kaminari-docker-events → KAMINARI-FUJI-Homelab
```

## DNS and TLS

| Hostname | Result |
|---|---|
| `ntfy.rplab.qzz.io` | HTTPS 200; valid certificate; canonical |
| `nfty.rplab.qzz.io` | HTTPS 502 from Cloudflare; not used |

The two names are not interchangeable. The typo remains in older documentation and was the cause of earlier 502 reports.

## Network paths

| Path | URL | Status |
|---|---|---|
| Public / remote | `https://ntfy.rplab.qzz.io` | verified 200 |
| LAN / CLI | `http://192.168.0.104:8201` | verified 200 from the Mint VM |
| Android app | `https://ntfy.rplab.qzz.io` | recommended; HTTPS enforced |
| PVE producers | `http://192.168.0.104:8201/KAMINARI-FUJI-Homelab` | verified config; not changed |

The app deliberately enforces HTTPS and does not opt into Android cleartext traffic, so the LAN HTTP origin is for CLI/producer use, not the phone.

## Proxmox and gateway

PVE API is `https://192.168.0.113:8006/api2/json`. The gateway is separate and is not deployed. The gateway identity is read-only and never embedded in the app. No new CT/VM was created for this work.
