# Kaminari Proxmox Gateway — Security Configuration Guide

This guide describes how the gateway is secured by default, what must be
configured before production, and what must never be relaxed casually.

## Default security posture

- Gateway token authentication required for all protected endpoints.
- Gateway token is compared in constant time.
- Rate limiting is applied per client key.
- Request size is limited at the gateway layer.
- Mutations are disabled by default.
- Mutation requests require an approval token equal to the command nonce.
- Nonces are replay-protected in memory for their expiry window.
- Requests to Proxmox must use HTTPS.
- Proxmox API token stays server-side.
- Logs are structured and audit events are logged without secrets.
- No shell, SSH, or unrestricted Proxmox access is provided.

## Required runtime configuration

Before production, the deployed `.env` must include at least:

- GATEWAY_GATEWAY_TOKEN
- GATEWAY_PROXMOX_BASE_URL
- GATEWAY_PROXMOX_API_TOKEN

Optional but important:

- GATEWAY_PROXMOX_VERIFY_TLS true in production
- GATEWAY_MUTATIONS_ENABLED false unless there is a documented need
- GATEWAY_RATE_LIMIT_PER_MINUTE tuned to the real deployment
- GATEWAY_MAX_REQUEST_BYTES tuned to the real deployment

## Secrets handling

- Generate the gateway token with a cryptographically random value.
- Store it in the deployed, git-ignored configuration, not in source.
- Do not log tokens or Proxmox API tokens.
- Do not pass secrets through the Android app as command payloads or topics
  in unsecured ways.
- Rotate the gateway token if it may have been exposed.
- Revoke and replace the Proxmox API token if it may have been exposed.

## Token generation example

python -c "import secrets; print(secrets.token_urlsafe(43))"

Use a long random token. Do not use a short password or a predictable value.

## Token storage

- Store the deployed .env with restricted permissions.
- Restrict access to the service account, not to a shared user.
- Back up the deployed configuration securely.

## Token rotation

1. Generate a new gateway token.
2. Update the deployed .env with the new token.
3. Validate the configuration.
4. Restart the gateway service.
5. Update the Android client for every profile using the gateway.
6. Remove the old token only after all clients have been updated.

A helper script is provided:
`deploy/scripts/rotate-token.sh`

It generates a new token and prints the owner steps. It does not deploy or
restart anything.

## Fail closed behavior

The gateway configuration is required to be valid at startup:
- Gateway token must be present.
- Proxmox base URL must use HTTPS.
- Proxmox API token must be present.

If required configuration is missing, the service should not start with unsafe
defaults. Use `deploy/scripts/validate-config.sh` before or after deployment as
part of operational readiness.

## Proxmox TLS verification

- Keep `GATEWAY_PROXMOX_VERIFY_TLS=true` in production.
- If a private CA is used, ensure the gateway host trusts it.
- Do not disable TLS verification simply to work around certificate issues.

## Network isolation

- Keep the gateway reachable only from the intended network or VPN.
- Do not expose it publicly by default.
- Use firewall rules, reverse proxy ACLs, and service binding together.
- Health and readiness endpoints should not be publicly exposed without the
  intended access model.

## Least privilege

- Use a dedicated Proxmox service account for the gateway.
- Grant only the permissions needed for the read-only operations used.
- Do not grant Administrator or equivalent broad permissions unless a specific
  documented requirement makes them unavoidable.
- Keep mutation permissions separate from read-only permissions.
- Keep mutations disabled in the gateway configuration unless there is a
  documented operational need.

## Logging constraints

- The gateway logs audit events without secrets under normal operation.
- Review logs for accidental credential exposure after any incident.
- Do not log full request bodies unless there is a specific operational need.

## Configuration drift

- Treat the deployed .env as the source of truth for runtime configuration.
- Keep infrastructure changes documented in the operations guide.
- After any change, re-run validation and health checks.
