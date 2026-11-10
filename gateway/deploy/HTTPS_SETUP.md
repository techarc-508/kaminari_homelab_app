# Kaminari Proxmox Gateway — HTTPS Setup Notes

The gateway is transport-secure only when TLS is in use. The current codebase
and configuration assume HTTPS for the Proxmox API and a private network or VPN
for client access.

## TLS termination options

1. Terminate TLS at a reverse proxy such as nginx.
2. Terminate TLS at uvicorn using `--ssl-keyfile` and `--ssl-certfile`.
3. Use a dedicated service mesh or internal TLS where the environment already
   supports it.

For a self-hosted Proxmox environment, a reverse proxy or uvicorn TLS is
usually the simplest path.

## Certificate choice

- Prefer a certificate the Android client already trusts.
- If a private PKI is used, install the private CA root into the Android trust
  store for the relevant profiles/devices, or use a publicly trusted wildcard
  or host certificate if that fits the network model.
- Do not disable Android TLS verification to accommodate a self-signed
  certificate. That weakens transport security and is not an acceptable
  default.

## Reverse proxy notes

If a reverse proxy is used:
- Terminate TLS at the proxy.
- Send requests to the gateway over localhost or a private segment.
- Keep the gateway bound to localhost unless the network layout requires
  otherwise.
- Keep the reverse proxy ACLs tight and aligned with the private-network
  requirement.
- Mirror request-size limits at the proxy layer as defense-in-depth.

An example nginx configuration is provided in
`deploy/reverse-proxy/nginx.conf.example`. It is an example, not a drop-in
public deployment.

## Gateway startup TLS

If uvicorn TLS is used instead of a reverse proxy:
- Provide real key and certificate material.
- Bind to the intended network interface.
- Keep the service reachable only from the intended network.
- Do not rely on `--host 0.0.0.0` plus TLS alone as the access control.

## Endpoints and authentication

- `/healthz` is unauthenticated and returns only a minimal status object.
- `/readyz` and the Proxmox-facing API paths require the gateway token.
- Do not expose internal administration or debug endpoints publicly.

## Validation

After setup:
- Run `deploy/scripts/health-check.sh` from the gateway host or from a host
  that can reach the intended internal URL.
- Verify that the Android client can reach the gateway over the intended
  network path.
- Verify that Proxmox API calls work with the configured service account before
  enabling any mutation path.
