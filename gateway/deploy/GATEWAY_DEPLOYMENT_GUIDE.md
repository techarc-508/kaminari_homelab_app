# Kaminari Proxmox Gateway — Deployment Guide

This guide describes how to deploy the Proxmox gateway for a self-hosted
homelab. It is documentation only. It does not deploy, create an LXC, or modify
production infrastructure.

## Target environment

- Dedicated Proxmox LXC where supported
- Minimal Linux installation
- Python virtual environment
- ASGI server for production serving
- systemd service running as a non-root service user
- HTTPS termination by reverse proxy or uvicorn
- Private-network or VPN-only access by default

## Repository layout

- `gateway/.env.example` — configuration template
- `gateway/deploy/systemd/kaminari-gateway.service` — systemd unit template
- `gateway/deploy/systemd/README.md` — systemd notes
- `gateway/deploy/reverse-proxy/nginx.conf.example` — reverse proxy example
- `gateway/deploy/HTTPS_SETUP.md` — HTTPS notes
- `gateway/deploy/GATEWAY_SECURITY_CONFIGURATION.md` — runtime security docs
- `gateway/deploy/PROXMOX_ACL_SETUP_GUIDE.md` — least-privilege Proxmox setup
- `gateway/deploy/scripts/validate-config.sh`
- `gateway/deploy/scripts/health-check.sh`
- `gateway/deploy/scripts/backup.sh`
- `gateway/deploy/scripts/restore.sh`
- `gateway/deploy/scripts/rotate-token.sh`

## Pre-deployment checklist

1. Decide the LXC/node identity, domain, and network model.
2. Decide HTTPS termination approach.
3. Create a dedicated Proxmox service account and API token.
4. Assign least-privilege read-only ACLs.
5. Generate the gateway token.
6. Write the deployed .env to a git-ignored, permission-restricted path.
7. Validate the configuration.
8. Install the service and start it.
9. Run health checks.
10. Confirm Proxmox read-only endpoints work with the configured account.
11. Decide mutation policy before enabling anything that changes Proxmox state.

## Installation steps

### 1. Prepare the host

Ensure Python 3 and pip are available on the target host. Decide where the
application will live, for example `/opt/kaminari-gateway`.

### 2. Deploy the code

Deploy the `gateway/` directory contents to the chosen location.

### 3. Create the virtual environment

python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

### 4. Deploy the configuration

Copy `gateway/.env.example` to the deployed config path, fill in real values,
and restrict access. Example:

cp .env.example /etc/kaminari-gateway/.env
chmod 600 /etc/kaminari-gateway/.env
chown kaminari-gateway:kaminari-gateway /etc/kaminari-gateway/.env

Then edit the file with real values.

### 5. Validate configuration

bash gateway/deploy/scripts/validate-config.sh

Adjust `DEPLOYED_ENV_FILE` if the deployed config is in a non-default location.

### 6. Install the systemd unit

Copy the unit file to the systemd unit directory:

cp gateway/deploy/systemd/kaminari-gateway.service /etc/systemd/system/

Edit the unit for the real user, group, working directory, and environment file
path.

### 7. Enable and start the service

systemctl daemon-reload
systemctl enable kaminari-gateway
systemctl start kaminari-gateway
systemctl status kaminari-gateway

## HTTPS

Use HTTPS for Proxmox API calls and for client access. See
`deploy/HTTPS_SETUP.md` and `deploy/reverse-proxy/nginx.conf.example`.

Do not expose the gateway publicly by default.

## Network access

Keep the gateway reachable only from the intended network or VPN. Combine:
- Binding to the intended interface
- Reverse proxy ACLs if used
- Firewall rules on the host and network

## Verify deployment

Run the health check from an internal host:

bash gateway/deploy/scripts/health-check.sh

If the gateway is behind a reverse proxy, set `HEALTHZ_URL` to a verified
internal URL before running the health check.

## Operational readiness verification

Before enabling mutations or exposing the gateway to Android clients:

- Validate configuration
- Confirm /healthz answers
- Confirm Proxmox read-only endpoints work with the real service account
- Confirm logs do not expose credentials in normal operation
- Confirm backup/restore procedures are documented and tested in a safe manner
- Confirm rollback is possible by restoring previous code/config and restarting

## Do not automate in this package

No script in this package:
- Creates an LXC
- Modifies Proxmox ACLs
- Changes firewall rules
- Deploys to production

Those actions are owner-executed administrative operations.

## Deployment decision points

- LXC versus other host: LXC is the preferred documented shape, but the exact
  host depends on the environment.
- Reverse proxy versus uvicorn TLS: either is acceptable; document the choice.
- Certificate authority: use a CA the Android clients trust or configure private
  PKI trust properly.
- Mutation policy: keep disabled unless there is a documented operational need.

## Related documents

- `GATEWAY_SECURITY_CONFIGURATION.md`
- `PROXMOX_ACL_SETUP_GUIDE.md`
- `GATEWAY_OPERATIONS_GUIDE.md`
- `GATEWAY_BACKUP_AND_RESTORE.md`
- `GATEWAY_DEPLOYMENT_VALIDATION_REPORT.md`
