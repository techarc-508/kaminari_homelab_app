#!/usr/bin/env bash
# Kaminari Proxmox Gateway — health check helper
#
# Usage:
#   bash gateway/deploy/scripts/health-check.sh
#
# This script checks the unauthenticated /healthz endpoint and reports whether
# the service is reachable. It does not authenticate and does not access
# Proxmox.
#
# Owner action: set the real local service URL if the gateway is not listening
# on localhost:8443.

ENDPOINT="${HEALTHZ_URL:-http://localhost:8443/healthz}"
echo "NOTE: this health check uses the local gateway URL. If the gateway is behind a reverse proxy, adjust HEALTHZ_URL to a verified internal URL." >&2

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required for this health check" >&2
  exit 1
fi

http_code=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 8 "$ENDPOINT" || true)

if [ "$http_code" = "200" ]; then
  echo "OK: gateway healthz returned 200 from $ENDPOINT"
  exit 0
else
  echo "FAIL: gateway healthz returned $http_code from $ENDPOINT" >&2
  exit 1
fi
