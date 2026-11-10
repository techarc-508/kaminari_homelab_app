# Kaminari Proxmox Gateway — Backup and Restore

This document describes what to back up, how to back up it, and how to restore
it for the deployed gateway.

## What matters for continuity

- Deployed configuration, especially:
  - GATEWAY_GATEWAY_TOKEN
  - GATEWAY_PROXMOX_BASE_URL
  - GATEWAY_PROXMOX_API_TOKEN
- The deployed systemd unit file, if it has been customized
- Any custom reverse proxy configuration, if used
- Certificate material if TLS termination is managed on the gateway host
- Virtual environment and dependency state if you want reproducible rebuilds

The gateway does not store durable Proxmox state. It talks to Proxmox as a
client. Proxmox is the system of record for the cluster. Back up Proxmox
separately through the normal Proxmox backup/backup-job process.

## What not to back up casually

- Do not back up secrets into locations that are not access-controlled.
- Do not back up into world-readable paths.
- Do not commit backup material to Git.

## Backup approach

Use the provided helper:

bash gateway/deploy/scripts/backup.sh

Review the list of files being backed up before using it in production. The
helper is intentionally conservative and backs up only a small set of example
paths by default.

Adjust `BACKUP_DIR` and the list of paths to match the real deployment.

## Restore approach

Use the provided helper:

bash gateway/deploy/scripts/restore.sh BACKUP_FILE

After restore:
1. Validate the configuration.
2. Confirm the restored files are in the correct locations.
3. Restart the service.
4. Confirm health and Proxmox connectivity.

## Disaster recovery

If the host is lost:

1. Recreate the LXC/host as documented in the deployment guide.
2. Deploy the gateway code.
3. Recreate the virtual environment.
4. Restore the deployed configuration from backup.
5. Restore any custom service/reverse-proxy config from backup.
6. Restore certificates if applicable.
7. Validate and start the service.
8. Re-create the Proxmox service account and token if the old token cannot be
   recovered securely.

Keep a separate, secure record of how to recreate the Proxmox service account
and token. Backing up the API token is acceptable only in a secured location,
not in shared or public storage.

## Backup retention

Review retention and deletion policy for backups that may contain secrets. The
helper prunes backups older than 30 days by default. Adjust or disable that
behavior to match the environment.

## Testing backups

Test restore in a safe environment before relying on backup files in a
disaster. Confirm that restored configuration works with the gateway service and
that the restored values can be read by the service account.
