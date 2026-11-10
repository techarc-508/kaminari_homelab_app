#!/usr/bin/env bash
# Kaminari Proxmox Gateway — configuration validation helper
#
# Usage:
#   bash gateway/deploy/scripts/validate-config.sh
#
# This script checks that the deployed configuration file exists and that the
# required variables are non-empty. It does not connect to Proxmox and it does
# not validate TLS certificates or DNS.
#
# Owner action: set DEPLOYED_ENV_FILE to the real deployed .env path.
set -euo pipefail

DEPLOYED_ENV_FILE="${DEPLOYED_ENV_FILE:-/etc/kaminari-gateway/.env}"

if [ ! -f "$DEPLOYED_ENV_FILE" ]; then
  echo "ERROR: deployed config file missing: $DEPLOYED_ENV_FILE" >&2
  exit 1
fi

missing=()
for var in GATEWAY_GATEWAY_TOKEN GATEWAY_PROXMOX_BASE_URL GATEWAY_PROXMOX_API_TOKEN; do
  val=$(grep -E "^${var}=" "$DEPLOYED_ENV_FILE" | head -n 1 | cut -d= -f2- || true)
  if [ -z "$val" ] || [ "$val" = "REPLACE_WITH_LONG_RANDOM_TOKEN" ] || [ "$val" = "REPLACE_WITH_PROXMOX_API_TOKEN" ] || [ "$val" = "https://proxmox.example:8006/api2/json" ]; then
    missing+=("$var")
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo "ERROR: the following required configuration values are missing or still using placeholder values:" >&2
  for v in "${missing[@]}"; do
    echo "  - $v" >&2
  done
  exit 1
fi

echo "OK: required config present in $DEPLOYED_ENV_FILE"
grep -E "^(GATEWAY_GATEWAY_TOKEN|GATEWAY_PROXMOX_BASE_URL|GATEWAY_PROXMOX_API_TOKEN)=" "$DEPLOYED_ENV_FILE" | cut -d= -f1
