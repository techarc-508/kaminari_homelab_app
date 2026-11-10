#!/usr/bin/env bash
# Kaminari Proxmox Gateway — backup helper
#
# Usage:
#   bash gateway/deploy/scripts/backup.sh
#
# This script backs up the deployed configuration and any gateway-managed local
# state that the owner wants to keep. It does not connect to Proxmox. It does
# not touch Proxmox ACLs or the Proxmox API.
#
# Owner action:
# - Set BACKUP_DIR to where backups should go.
# - Adjust the list of paths to back up for the real deployment.
# - Decide whether Proxmox API tokens, systemd unit changes, cert material, or
#   nginx configs should be backed up separately.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/kaminari-gateway/backups}"
DEPLOYED_ENV="${DEPLOYED_ENV_FILE:-/etc/kaminari-gateway/.env}"
mkdir -p "$BACKUP_DIR"

timestamp=$(date +%Y%m%dT%H%M%S)
archive="$BACKUP_DIR/kaminari-gateway-backup-$timestamp.tar.gz"

# Example paths to back up. Review before using in production.
files_to_backup=()
if [ -f "$DEPLOYED_ENV" ]; then
  files_to_backup+=("$DEPLOYED_ENV")
fi

if [ ${#files_to_backup[@]} -eq 0 ]; then
  echo "WARNING: nothing to back up with current configuration" >&2
  exit 0
fi

tar -czf "$archive" "${files_to_backup[@]}"

echo "OK: backup written to $archive"

# Remove backups older than 30 days. Adjust or disable as needed.
find "$BACKUP_DIR" -name "kaminari-gateway-backup-*.tar.gz" -type f -mtime +30 -delete || true
