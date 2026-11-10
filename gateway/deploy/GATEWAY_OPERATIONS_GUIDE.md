# Kaminari Proxmox Gateway — Operations Guide

This guide covers day-to-day operational tasks for the deployed gateway.

## Service status

systemctl status kaminari-gateway

Useful commands:

systemctl is-active kaminari-gateway
systemctl is-enabled kaminari-gateway
systemctl reload-or-restart kaminari-gateway
systemctl stop kaminari-gateway
systemctl start kaminari-gateway

## Viewing logs

journalctl -u kaminari-gateway -f
journalctl -u kaminari-gateway --since today
journalctl -u kaminari-gateway --since "1 hour ago"

If logs are also written to files, use the configured log path instead.

## Health and readiness

Unauthenticated health:
GET /healthz

Authenticated readiness:
GET /readyz

The `/readyz` endpoint checks Proxmox connectivity. It requires the gateway
token. Use it to confirm end-to-end Proxmox reachability, not just process
health.

## Configuration validation

Before restarts and after config changes:

bash gateway/deploy/scripts/validate-config.sh

Adjust `DEPLOYED_ENV_FILE` to the real deployed config path if needed.

## Service user and filesystem permissions

- The service should run as a dedicated non-root user.
- The deployed .env should be readable only by the service account.
- The application directory should be readable by the service account.
- Logs should be written to a path the service account can write to, or to the
  journal.

## Restart policy

The unit restarts on failure with a short backoff. Use this for controlled
restarts after config or code changes. For upgrades and rollback, stop the
service, replace the code or config snapshot, validate, then start.

## Graceful shutdown

The unit allows a brief shutdown window. During upgrades, stop the service,
apply changes, validate, then restart. Do not tear down the host or LXC while
active requests may be in flight.

## Request size and rate limits

- Request size is limited at the gateway layer.
- Rate limiting is applied per client key.
- Tune `GATEWAY_RATE_LIMIT_PER_MINUTE` and `GATEWAY_MAX_REQUEST_BYTES` for the
  real deployment if needed.

## Mutations

- Mutations are disabled by default.
- If mutations are enabled, the approval token must equal the command nonce.
- Keep mutation ACLs separate from read-only ACLs.
- Review every mutation endpoint before enabling it.
- Prefer read-only operations for monitoring and diagnostics.

## Incident recovery

If the gateway becomes unhealthy:

1. Check service status and logs.
2. Validate configuration.
3. Check Proxmox connectivity with `/readyz`.
4. If configuration is suspect, restore from backup and restart.
5. If the gateway token may be compromised, rotate it and update the Android
   clients.
6. If the Proxmox API token may be compromised, revoke and replace it.
7. Confirm all clients have been updated before revoking old credentials.

## Token rotation

1. Generate a new token.
2. Update the deployed .env.
3. Validate the configuration.
4. Restart the gateway.
5. Update the Android client for every profile that uses the gateway.
6. Remove the old token after confirmation.

Helper: `deploy/scripts/rotate-token.sh`

## Backup

Run the backup helper periodically:

bash gateway/deploy/scripts/backup.sh

Adjust `BACKUP_DIR` and the list of paths to back up for the real deployment.

## Restore

Restore from a previously created backup:

bash gateway/deploy/scripts/restore.sh BACKUP_FILE

Review the restored file locations before restarting.

## Upgrade

1. Stage the new code/config snapshot.
2. Validate the new configuration.
3. Run the gateway test suite in the staging phase if applicable.
4. Stop the service.
5. Replace the deployed code/config.
6. Validate again.
7. Start the service.
8. Confirm health and Proxmox connectivity.

## Rollback

1. Stop the service.
2. Restore the previous code and/or configuration snapshot.
3. Validate.
4. Start the service.
5. Confirm health and Proxmox connectivity.

## Log review after incidents

Search logs for:
- Authentication failures
- Rate limit events
- Proxmox errors
- Replay attempts
- Command failures

Do not log or store full gateway tokens or Proxmox API tokens in incident notes.
Use redacted references only.

## Operational boundaries

This service does not provide:
- Shell access
- SSH
- Unrestricted Proxmox access
- Direct VM/CT control outside the typed command interface

Operations on Proxmox VMs, containers, storage, or cluster configuration should
go through the typed, allowlisted gateway interface when mutations are enabled,
and only with the documented ACLs.
