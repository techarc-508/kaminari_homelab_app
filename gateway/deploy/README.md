# Kaminari Proxmox Gateway — Deployment Package

This directory contains the production deployment scaffold for the Kaminari
Proxmox Gateway.

It is prepared for a dedicated, restricted Proxmox LXC. It is intentionally not
a public-facing service.

## What is in this package

- `gateway/.env.example` — configuration template
- `gateway/deploy/systemd/kaminari-gateway.service` — systemd unit template
- `gateway/deploy/reverse-proxy/nginx.conf.example` — reverse proxy example
- `gateway/deploy/scripts/validate-config.sh` — configuration validation
- `gateway/deploy/scripts/health-check.sh` — health check helper
- `gateway/deploy/scripts/backup.sh` — backup helper
- `gateway/deploy/scripts/restore.sh` — restore helper
- `gateway/deploy/scripts/rotate-token.sh` — owner-run token rotation helper

## What is intentionally not in this package

No script in this package creates an LXC, modifies Proxmox ACLs, changes
firewall rules, or deploys anything to production. Those actions require
owner execution.

## Recommended deployment shape

- Dedicated unprivileged LXC where supported
- Minimal Linux installation
- Python virtual environment
- FastAPI served by a production ASGI server
- systemd service running as a non-root service user
- HTTPS termination via reverse proxy or uvicorn TLS settings
- Private network or VPN-only access by default
- Proxmox API access through a dedicated least-privilege service account

## Quick overview of runtime secrets

- `GATEWAY_GATEWAY_TOKEN` — Android client auth token
- `GATEWAY_PROXMOX_BASE_URL` — Proxmox API base URL
- `GATEWAY_PROXMOX_API_TOKEN` — Proxmox service account API token

These values must be real before production startup and must not be committed
to Git.

## Related docs

- `GATEWAY_SECURITY_CONFIGURATION.md`
- `PROXMOX_ACL_SETUP_GUIDE.md`
- `GATEWAY_OPERATIONS_GUIDE.md`
- `GATEWAY_BACKUP_AND_RESTORE.md`
- `GATEWAY_DEPLOYMENT_GUIDE.md`
