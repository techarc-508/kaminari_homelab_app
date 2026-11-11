# Kaminari Nfty — Existing ntfy Infrastructure Audit

**Stage:** Phase 8, Stage 4A.
**Start commit:** `381235c`
**Date:** 2026-11-10

## 1. Summary

This stage discovered the existing ntfy infrastructure in the RPLab homelab and
evaluated whether the existing instance can be reused by Kaminari Nfty.

The key finding is that there is a running ntfy instance on the Proxmox host
(`pve`, <private-pve-host>), but the well-known public URL
`https://nfty.rplab.qzz.io` returned `502` from the Proxmox host at audit time,
and the public endpoint could not be verified as the same ntfy instance from
this machine.

So:
- A ntfy process exists and appears healthy on the internal host.
- The reachable public URL `https://nfty.rplab.qzz.io` is not currently usable
  from the Proxmox host for API access at audit time.
- The exact relationship between `nfty.rplab.qzz.io` and the internal ntfy
  process is not fully confirmed from this environment.

## 2. Discoverable hosts and access

| Item | Value | Source | Notes |
|------|-------|--------|-------|
| Proxmox host | `pve`, <private-pve-host> | RPLAB_ACCESS.md + SSH | Verified reachable over SSH from this VM with key |
| Linux Mint VM | inside Proxmox | RPLAB_ACCESS.md | Known desktop VM used for this work |
| ntfy host process | on `pve` | live SSH inspection | `ntfy serve` running as pid 8671 |
| ct104 | <private-ntfy-lan> | RPLAB_ACCESS.md | Not reachable from this SSH session via the key copied to pve for this audit; direct root@<private-ntfy-lan> access did not succeed in this run |

## 3. ntfy instance discovery

### 3.1 Process on Proxmox host
- `ntfy serve` is running on `pve`.
- Process uid/gid appears as `100000`.
- Working directory appears to be a minimal root filesystem, consistent with a
  container-like or isolated runtime environment.
- A `/data` tree exists and looks like ntfy data directories:
  - `archive`
  - `declined`
  - `incoming`
  - `live`
  - `rejected`
  - `staged`

### 3.2 Binary and service identity
- The `ntfy` binary was not found at the usual user paths searched during this
  audit.
- There is no `ntfy.service` unit on the host.
- No crontab or supervisor entry for ntfy was found in the locations searched.
- The process appears to have been started in a way that is not fully visible
  from the host-level unit/cron/supervisor files examined during this audit.

### 3.3 HTTPS/TLS placement
- The ntfy process on `pve` appears to be listening on its own internal account
  and may not be the TLS-terminating public endpoint itself.
- Public HTTPS for `nfty.rplab.qzz.io` is Cloudflare-managed.
- The Cloudflare proxy returned `502` when accessed from `pve` at audit time.

### 3.4 DNS
- `nfty.rplab.qzz.io` resolves to:
  - `104.21.63.209`
  - `172.67.150.94`
- These are Cloudflare addresses, consistent with a Cloudflare-proxied origin.

## 4. Observed reachability status

| Endpoint | Observed from | Result at audit time | Interpretation |
|----------|---------------|----------------------|----------------|
| https://nfty.rplab.qzz.io (root) | pve via curl | HTTP 502 | Cloudflare returned 502 |
| https://nfty.rplab.qzz.io/api/v1/config | pve via curl | HTTP 502 | Same |
| https://nfty.rplab.qzz.io/api/v1/topics | pve via curl | HTTP 502 | Same |
| https://nfty.rplab.qzz.io/api/v1/topics (with -u guest:) | pve via curl | HTTP 502 | Same |
| `ntfy serve` process on pve | live SSH | running | internal ntfy present |

Important: a 502 from Cloudflare usually means Cloudflare could not get a valid
response from the origin server at that moment. It does **not** necessarily mean
the origin ntfy server is permanently down. It may mean:
- the origin is temporarily unreachable from Cloudflare,
- TLS/port/routing changed,
- the origin is not the same host that the public URL points at,
- or the public URL is not currently the correct live ntfy address.

## 5. Existing topic inventory

Topic inventory was **not** obtained from the live API because the public URL
returned 502 from the audit host at audit time.

However, the repository itself describes a likely existing topic layout through
sample data. These are **repository-described intended topics**, not confirmed
live topics:

| Topic | Where seen | Likely purpose |
|-------|------------|----------------|
| `homelab-alerts` | SampleDataProvider | Homelab/PVE alerts |
| `iot-telemetry` | SampleDataProvider | IoT/telemetry use |
| `backups` | SampleDataProvider | Backup-related alerts |
| `build-server` | SampleDataProvider | Build/gateway events |
| `security` | SampleDataProvider | Security notifications |

These topics are referenced by sample actions pointing at
`https://nfty.rplab.qzz.io/...` paths, so they appear to be the intended
production/consumption topics for the existing ntfy deployment model.

## 6. Access model

From repository code:
- Default profile/server config uses `serverUrl = "https://nfty.rplab.qzz.io"`.
- Default `AuthMethod` is `GUEST`.
- The app supports:
  - guest/anonymous,
  - username+password,
  - access token,
  - `useHttpsOnly = true` by default.

From infrastructure:
- RPLAB_ACCESS.md lists several service credentials, but **no ntfy-specific
  username/password/token was visible in that file as used here.**
- The existing ntfy deployment likely has its own auth and topic ACLs, but the
  exact ntfy users, roles, and topic ACLs were not enumerated because the live
  API was not reachable from the audit host at audit time.

## 7. Existing integrations observed or likely

Likely from RPLAB_ACCESS.md and live process inspection:
- The Proxmox host runs several other services.
- A separate Kaminari-related server process exists on the host
  (`/opt/server/venv/bin/uvicorn server:app --host 0.0.0.0 --port 8080`).
- Immich-related services are present.
- Apprise-related services appear present.
- The existing `Kaminari Media Gateway` account referenced in RPLAB_ACCESS.md
  is a different service from ntfy.

No existing ntfy publisher/consumer integration was confirmed live in this
audit beyond the existence of the ntfy process itself.

## 8. PVE notification configuration

PVE-native notification configuration was not directly enumerated in a way that
confirmed ntfy as the active PVE notification driver during this audit. If PVE
uses ntfy for its own notifications, that configuration would need to be checked
with the owner or from Proxmox directly in a later read-only review.

## 9. Guest and superuser access

Not confirmed from live API:
- guest access behavior,
- user list,
- topic ACLs,
- superuser/admin separation.

Repository code expects guest or authenticated access depending on profile
configuration, and the audit should treat existing ACLs as **unknown until
live verification succeeds**.

## 10. Conclusion on reuse

Reuse is plausible but **not yet verified**:
- There is a real ntfy instance on the Proxmox host.
- There is a real public URL referenced by the repository.
- The connection between them is currently broken or not verifiable from the
  audit host.
- The existing topic names described in the repository give a strong hint of how
  the app should map its features.

Because the public endpoint returned 502 from the internal host, Stage 4
cannot complete live topic reuse confirmation now. The integration plan in the
next section is therefore based on **repository evidence plus infrastructure
discovery**, with live verification marked as a required owner action.

## 11. What remains unverified

- Live ntfy server version from this environment.
- Live topic list.
- Live guest vs authenticated access behavior.
- Live admin/user ACL state.
- Whether `nfty.rplab.qzz.io` is currently the correct production ntfy URL.
- Whether the Kaminari Nfty-specific topics already exist.
- Whether PVE notifications already use ntfy.
- Whether the Proxmox-host ntfy and the Cloudflare URL are the same endpoint.

## 12. Evidence notes

- Live observations were made from `pve` via SSH from this VM using the existing
  SSH key.
- Public URL observations were made from `pve`, not from the public internet,
  so they reflect Cloudflare-origin reachability from the homelab side.
- No secrets were copied into repository files. RPLAB_ACCESS.md remained outside
  the repository and was used only as a read-only access reference.
