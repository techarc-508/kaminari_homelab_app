#!/usr/bin/env bash
# Kaminari Proxmox Gateway — restore helper
#
# Usage:
#   bash gateway/deploy/scripts/restore.sh BACKUP_FILE
#
# This script restores a previously created gateway configuration backup.
# It does not connect to Proxmox. It does not validate Proxmox ACLs.
#
# Owner action:
# - Review which debian.conf-path-style paths are being restored.
# - Ensure restored files go to the correct deployment locations.
# - After restore, run validate-config.sh and restart the service.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 BACKUP_FILE" >&2
  exit 1
fi

BACKUP_FILE="$1"
DEPLOYED_ENV="${DEPLOYED_ENV_FILE:-/etc/kaminari-gateway/.env}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: backup file missing: $BACKUP_FILE" >&2
  exit 1
fi

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

tar -xzf "$BACKUP_FILE" -C "$tmpdir"

restored_env=$(find "$tmpdir" -maxdepth 1 -name "*.env" -type f | head -n 1 || true)
if [ -z "$restored_env" ]; then
  echo "ERROR: no .env file found in backup: $BACKUP_FILE" >&2
  exit 1
fi

# Restore the gateway environment file only. Adjust if the real deployment
# backs up more files.
mkdir -p "$(dirname "$DEPLOYED_ENV")"
cp "$restored_env" "$DEPLOYED_ENV"

echo "OK: restored gateway environment file to $DEPLOYED_ENV"
