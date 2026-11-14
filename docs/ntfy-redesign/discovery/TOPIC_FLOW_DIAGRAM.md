# Proposed Topic Flow Diagram

```mermaid
flowchart LR
  subgraph S["Sources"]
    WEB["RPLab website / gate"]
    PVEH["PVE host"]
    PVECT["LXC guests"]
    PVEVM["VMs"]
    ZFS["ZFS / SMART / Scrutiny"]
    BK["Backups / Healthchecks"]
    NET["Uptime Kuma / DNS / TLS / tunnel"]
    SVC["Docker services"]
    SEC["CrowdSec / auth logs"]
    NTFY["ntfy health probe"]
    APP["Kaminari Android app"]
    GW["Command gateway / executor"]
    DIG["Digest job"]
  end

  subgraph T["ntfy topics"]
    T1["rplab-web"]
    T2["rplab-pve-host"]
    T3["rplab-pve-ct"]
    T4["rplab-pve-vm"]
    T5["rplab-storage"]
    T6["rplab-backups"]
    T7["rplab-network"]
    T8["rplab-services"]
    T9["rplab-security"]
    T10["rplab-ntfy"]
    T11["rplab-command-request"]
    T12["rplab-command-result"]
    T13["rplab-digest"]
  end

  WEB --> T1
  PVEH --> T2
  PVECT --> T3
  PVEVM --> T4
  ZFS --> T5
  BK --> T6
  NET --> T7
  SVC --> T8
  SEC --> T9
  NTFY --> T10
  APP --> T11
  GW --> T12
  DIG --> T13

  T1 --> APP
  T2 --> APP
  T3 --> APP
  T4 --> APP
  T5 --> APP
  T6 --> APP
  T7 --> APP
  T8 --> APP
  T9 --> APP
  T10 --> APP
  T11 --> GW
  T12 --> APP
  T13 --> APP
```

## Access direction

- Producers **write only** their own topic.
- The Android app **reads** alert topics and `rplab-command-result`.
- The app **writes only** `rplab-command-request` (and the temporary onboarding test topic).
- The gateway **reads** requests and **writes** results.
- `kfadmin` is an administrator and is never configured in the Android app.
- No producer writes to a generic catch-all topic.

## Failure-path rule

If ntfy, Cloudflare, CT104, or the tunnel is down, the monitor that reports it must not depend on the same path. Use an external Uptime Kuma/Healthchecks vantage point and a fallback channel (email/Apprise) for `rplab-ntfy` and `rplab-network` critical events.
