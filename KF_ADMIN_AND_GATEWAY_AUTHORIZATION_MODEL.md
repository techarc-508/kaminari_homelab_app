# KF Admin and Gateway Authorization Model

**Status:** Design locked. **No account, token, gateway deployment or PVE permission was changed in Stage 1.**

## 1. Two separate planes

| Plane | Identity | Authority | Secret location |
|---|---|---|---|
| ntfy administration | `kfadmin` | ntfy users, topics, ACLs, tokens, server config | Owner password manager / interactive CLI |
| Application/gateway | `kaminari_app` | Read notifications; issue typed commands; read results/audit | Android Keystore + gateway secret store |

The two planes must not share a token.

- `kfadmin` is the **only** ntfy superadministrator.
- `kaminari_app` is a **normal ntfy user**. It is an **owner-level gateway role**, not an ntfy administrator.
- The Android APK never contains `kfadmin` credentials or a PVE root credential.
- The gateway never receives `kfadmin`'s token.

## 2. ntfy administration

`kfadmin` is used only for:

- User creation/deletion.
- Topic ACL changes.
- Token issue/revocation.
- Server configuration and version updates.
- Incident response.

Rules:

- HTTPS-only administration.
- MFA at the reverse proxy/identity layer if available; ntfy itself has no MFA.
- Random strong password; no reuse of PVE, Mint or previously exposed passwords.
- No admin token in the APK.
- No admin actions from notification messages.
- All admin actions logged outside ntfy where possible.

## 3. Application/gateway roles

| Gateway role | Holder | Permissions |
|---|---|---|
| `owner` | `kaminari_app` | Full approved read/operations, including destructive operations with step-up |
| `operator` | optional future | Non-destructive operations, no ACL/user changes |
| `viewer` | optional future | Read-only |
| `service` | gateway process | Only the exact API calls needed |

The gateway is the **only** component allowed to call the PVE API with a service token. Android calls the gateway over HTTPS, never PVE directly.

## 4. Gateway authentication

The gateway must provide:

- HTTPS only.
- OIDC or equivalent identity provider login, or a local account with MFA.
- Short-lived access tokens (15 minutes recommended).
- Rotating refresh tokens.
- Device/session revocation.
- Rate limiting and lockout.
- Server-side authorization on every route.
- Audit of actor, action, target, result and timestamp.
- CSRF protection for browser sessions.
- No anonymous routes except `/healthz` and `/readyz` with no sensitive data.
- No raw PVE API passthrough.
- No unrestricted shell endpoint.

## 5. PVE service identity

Current Stage 1 state (read-only, verified):

| Item | Value |
|---|---|
| Account | `kaminari-gateway@pve` |
| Token ID | `gateway-ro` |
| Privilege separation | Enabled |
| Role | `KaminariRead` |
| ACL path | `/` |
| Privileges | `Datastore.Audit`, `Sys.Audit`, `VM.Audit` |
| Mutations | **None** |

This is sufficient for Level 4 **read** surfaces. Level 4 write operations require a **separate, explicitly approved** role/token after the gateway’s typed operation catalogue is implemented and tested. Do not widen the current token for convenience.

## 6. Level 4 authorization

All mutating operations require:

1. Authenticated `owner` session.
2. Typed operation ID from a fixed catalogue.
3. Exact target validated against the live inventory.
4. Preview with current state and intended change.
5. Risk classification.
6. For destructive operations: step-up reauthentication within 5 minutes.
7. Explicit confirmation of the exact target string.
8. Server-side policy check independent of the client.
9. Audit record before execution and result after execution.
10. Idempotency key and replay protection.

No natural-language text is executed as shell. The language model may only select a typed operation and fill validated parameters.

## 7. Revocation

```bash
# ntfy
ntfy token remove --user kaminari_app 'Kaminari Android'
ntfy token remove --user kaminari_app 'Kaminari Gateway'
ntfy user remove kaminari_app

# PVE
pveum user token remove kaminari-gateway@pve gateway-ro
pveum user delete kaminari-gateway@pve
```

Revoking the gateway token disables all gateway PVE access. Revoking the Android token does not affect the gateway, and vice versa.

## 8. Security invariants

- No PVE root password in Android, gateway code, ntfy message or Git.
- No `kfadmin` token in Android or gateway.
- No anonymous ntfy write after migration.
- No command execution from free-text notification content.
- No destructive operation without preview, exact target, step-up and audit.
- No secret in logs, screenshots, support bundles or reports.
- No broad `kf-*` write permission for a producer.
