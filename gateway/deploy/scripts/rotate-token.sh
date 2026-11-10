#!/usr/bin/env bash
# Kaminari Proxmox Gateway — owner-run token rotation helper
#
# Usage:
#   bash gateway/deploy/scripts/rotate-token.sh
#
# This script generates a new gateway auth token and prints instructions for
# completing the rotation. It does not connect to any service and it does not
# deploy anything. The actual restart and Android client update are owner-run
# steps.
#
# Owner action:
# - After running this script, replace the old value of GATEWAY_GATEWAY_TOKEN
#   in the deployed .env file with the new value.
# - Run validate-config.sh.
# - Restart the gateway service.
# - Update the gateway token in the Android app configuration for all
#   profiles that use the gateway.
# - Keep the old token usable only until all clients have switched, then revoke
#   it by removing it from the deployed configuration and restarting.

set -euo pipefail

NEW_TOKEN=$(python3 -c "import secrets; print(secrets.token_urlsafe(43))")
echo "New gateway token generated."
echo
echo "Owner steps:"
echo "1. Replace GATEWAY_GATEWAY_TOKEN in the deployed .env file with:"
echo "   GATEWAY_GATEWAY_TOKEN=$NEW_TOKEN"
echo "2. Run: bash gateway/deploy/scripts/validate-config.sh"
echo "3. Restart the gateway service."
echo "4. Update the Android client gateway token for every profile using it."
echo "5. Confirm the old token is no longer in use, then remove it."
echo
echo "Do not paste or commit this token into logs, issue trackers, or Git."
