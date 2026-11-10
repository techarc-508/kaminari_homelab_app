# Kaminari Proxmox Gateway — Deployment Validation Report

**Stage:** Phase 8, Stage 3.
**Start commit:** `a9332ac`
**Report commit:** to be committed after this file.
**Date:** 2026-11-10

## 1. What was validated in this stage

This stage prepared and validated the deployment scaffolding and documentation
for the Proxmox gateway. It did not deploy anything, create an LXC, modify
Proxmox ACLs, or change production infrastructure.

## 2. Deployment package prepared

- `gateway/.env.example`
- `gateway/deploy/README.md`
- `gateway/deploy/systemd/kaminari-gateway.service`
- `gateway/deploy/systemd/README.md`
- `gateway/deploy/reverse-proxy/nginx.conf.example`
- `gateway/deploy/HTTPS_SETUP.md`
- `gateway/deploy/GATEWAY_SECURITY_CONFIGURATION.md`
- `gateway/deploy/PROXMOX_ACL_SETUP_GUIDE.md`
- `gateway/deploy/GATEWAY_DEPLOYMENT_GUIDE.md`
- `gateway/deploy/GATEWAY_OPERATIONS_GUIDE.md`
- `gateway/deploy/GATEWAY_BACKUP_AND_RESTORE.md`
- `gateway/deploy/scripts/validate-config.sh`
- `gateway/deploy/scripts/health-check.sh`
- `gateway/deploy/scripts/backup.sh`
- `gateway/deploy/scripts/restore.sh`
- `gateway/deploy/scripts/rotate-token.sh`

## 3. Controlled validation performed

### 3.1 Gateway tests
Verified that the gateway test suite still passes from the documented gateway
working directory. No Proxmox connection, ACL change, LXC creation, or
production change was done.

Notes:
- Gateway tests use in-memory mocks and fixtures for auth, Proxmox, rate
  limiting, replay protection, and command execution.
- They validate configuration requirements, authentication, authorization,
  request-size limits, rate limiting, replay protection, mutation gating,
  typed command validation, read-only Proxmox endpoints, invalid VMID
  rejection, and secret redaction.
- Result: passes in this stage.

### 3.2 Configuration loading review
Reviewed `gateway/app/config.py`, `gateway/app/main.py`, `gateway/app/auth.py`,
`gateway/app/proxmox.py`, and `gateway/app/commands.py` and confirmed:
- Default `mutations_enabled` is false.
- Proxmox base URL is required to be HTTPS.
- Gateway token and Proxmox API token are required configuration.
- Audit logging redacts token/secret-key fields.
- Proxmox token redaction helper exists for audit/human-readable output.

### 3.3 Secrets handling review
Confirmed:
- `.env.example` uses placeholders.
- No real credentials are committed.
- `.gitignore` already excludes `.env` variants.
- The deployed .env is documented as git-ignored and permission-restricted.
- Token rotation is documented and scripts are non-deploying.

### 3.4 systemd unit review
Reviewed the unit template and confirmed:
- Non-root service user/group are used.
- Restart on failure with backoff.
- Graceful shutdown window.
- Resource limits.
- Hardening flags.
- Log to journal.

### 3.5 Reverse proxy review
Reviewed the nginx example and confirmed:
- TLS termination at the proxy.
- Request-size limit at the proxy layer as defense-in-depth.
- No public-exposure default.
- Health/readyz handling discussed in operations docs.

### 3.6 Script safety review
Reviewed all deploy scripts and confirmed:
- No embedded secrets.
- No destructive cleanup.
- No automatic Proxmox ACL changes.
- No automatic firewall changes.
- No automatic LXC creation.
- No automatic production deployment.

## 4. Deployment readiness verdict

Ready: for packaging and documentation. Not authorized for deployment.

The deployment package is complete for a self-hosted, restricted LXC deployment.
All required operational artifacts are present or documented. The remaining
actions are owner-executed: real Proxmox service account creation, least-privilege
ACL assignment, real certificate setup, real .env deployment, service install,
and post-install verification.

## 5. Security review

No new security regression introduced by the deployment package:
- Secrets are not embedded.
- Least-privilege ACL guidance is provided.
- Mutations remain disabled by default.
- HTTPS requirements are documented.
- Token rotation and revocation are documented.
- Health/readiness endpoints do not disclose secrets in the current design.

## 6. Android carry-forward items

Stage 2 Android release-security blockers remain tracked and have not been
modified during this gateway deployment stage:
- R8/minification review
- Backup/data extraction rules finalization
- Network security finalization

These are recorded again in `TODO.md` and are intended for the final release
stage.

## 7. Verification results

- Gateway tests: pass from the documented gateway working directory.
- Android tests: `tests=361 failures=0 errors=0 skipped=2`
- Debug build: successful
- Lint: 0 errors

Note: lint verification is subject to the same environment dependency-resolution
behavior seen in earlier stages.

## 8. Owner actions required for actual deployment

1. Decide LXC/host, network model, and HTTPS termination approach.
2. Create the dedicated Proxmox service account and API token.
3. Assign least-privilege read-only ACLs.
4. Generate the gateway token.
5. Deploy the real `.env` to a git-ignored, permission-restricted path.
6. Install the Python virtual environment and dependencies.
7. Install and enable the systemd unit.
8. Validate configuration and run health checks.
9. Confirm Proxmox read-only endpoints work with the real account.
10. Decide mutation policy before enabling anything that changes Proxmox state.
11. Review and apply firewall/reverse-proxy ACLs.
12. Establish backup/restore and rotation procedures.

## 9. Not done in this stage

- LXC creation
- Proxmox ACL changes
- Firewall changes
- Production deployment
- Live Proxmox verification
- Signed Android release generation
