# Security Audit Report — Phase 7

**Status:** Review complete.
**Last updated:** 2026-11-10

## Scope

Gateway FastAPI service, Android `GatewayCommandExecutor`, `NtfyAdminClient`,
command envelope validation, and repository security posture.

## Findings

### Resolved in Phase 7

1. **Path parameter validation on gateway read endpoints.**
   - Risk: malformed `node` or `vmid` values could be interpolated into Proxmox
     paths.
   - Mitigation: strict `NODE_PATTERN` and `VMID_PATTERN` validation before
     path construction.

2. **Oversized gateway responses on Android.**
   - Risk: a compromised or misbehaving gateway could force large response
     allocation.
   - Mitigation: `GatewayCommandExecutor` rejects responses above 1 MiB using
     both `Content-Length` and actual body size.

3. **Missing default HTTP timeouts in ntfy admin client.**
   - Risk: indefinite waits on unresponsive ntfy servers.
   - Mitigation: explicit connect/read/write timeouts.

### Verified Controls

- Gateway bearer-token authentication remains required for all non-health
  endpoints.
- Rate limiting remains enabled.
- Request body size limit remains enforced via `Content-Length`.
- Replay protection remains enabled for command execution.
- Mutations remain disabled unless `GATEWAY_MUTATIONS_ENABLED=true`.
- Mutation approval requires a token matching the command nonce.
- Android gateway executor uses HTTPS by default and platform TLS validation.
- No shell, SSH, or arbitrary URL proxying is present.
- Proxmox credentials remain server-side.

### Residual Risks / Conditions

- Chunked requests without `Content-Length` are not rejected by the current
  middleware; production deployments should also enforce limits at the reverse
  proxy.
- Path validation is pattern-based and must be kept in sync with any future
  endpoint additions.
- No live security testing, penetration testing, or device validation was
  performed.
- Secrets handling depends on deployment hygiene and is not validated here.
