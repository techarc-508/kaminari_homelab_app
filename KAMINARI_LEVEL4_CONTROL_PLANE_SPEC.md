# Kaminari Level 4 Control Plane Specification

**Status:** Functional specification for Freebuff. **Not implemented in Stage 1.**
**Infrastructure:** PVE 9.2.11, single node `pve`; CT100–CT104; VM200; Docker stack on CT104; ntfy 2.28.0; website `rplab.qzz.io`.

## 1. Goal

A web and Android control plane that provides the owner with **full authorized administration** of the homelab, including destructive operations, through a secure typed-operation gateway. Natural-language input is translated into a fixed operation catalogue; it is never executed as shell.

## 2. Architectural boundary

```
Android / Web UI
      |
      | HTTPS + OIDC/MFA + short-lived session
      v
Kaminari Gateway (FastAPI or equivalent)
      |
      | typed operation -> policy -> preview -> confirm -> execute -> audit
      v
PVE API (least privilege) / Docker API / website admin API / ntfy
```

- Android never calls the PVE API directly.
- Gateway never exposes a raw PVE API proxy or unrestricted SSH.
- PVE root credentials are not stored in the gateway.
- The gateway uses a dedicated PVE token. Read-only now; write scope added only after approval.

## 3. Operation catalogue

Every operation has: `operation_id`, display name, target type, parameters, required privilege, risk level, validation, preview, rollback note, confirmation policy, audit fields.

### 3.1 Read / overview

| ID | Operation | Target | Risk |
|---|---|---|---|
| `pve.node.list` | Node inventory and health | node | L0 |
| `pve.node.status` | CPU/RAM/disk/network/uptime | node | L0 |
| `pve.guest.list` | CT and VM inventory | node | L0 |
| `pve.guest.inspect` | Guest config, status, agent, disks | guest | L0 |
| `pve.storage.list` | Storage capacity/health | storage | L0 |
| `pve.storage.inspect` | ZFS/pool/SMART summary | storage | L0 |
| `pve.task.list` | Recent tasks and status | node | L0 |
| `pve.task.log` | Sanitized task log | task | L0 |
| `pve.network.list` | Bridges, interfaces, addresses | node | L0 |
| `pve.firewall.list` | Rules and status | node/guest | L0 |
| `pve.user.list` | Accounts, roles, tokens (no secrets) | node | L0 |
| `pve.update.status` | Package/update state | node | L0 |
| `docker.list` | Containers | CT104 | L0 |
| `docker.inspect` | Config, health, resources | container | L0 |
| `docker.logs` | Sanitized bounded logs | container | L0 |
| `web.health` | Site, API, TLS, tunnel | website | L0 |
| `ntfy.health` | Health, version, auth mode | ntfy | L0 |

### 3.2 Lifecycle (reversible/disruptive)

| ID | Operation | Risk | Notes |
|---|---|---|---|
| `pve.guest.start` | Start CT/VM | L1 | Preview current state |
| `pve.guest.shutdown` | Graceful shutdown | L2 | Timeout + fallback policy |
| `pve.guest.stop` | Forced stop | L2 | Confirm; data-loss warning |
| `pve.guest.reboot` | Reboot | L2 | Confirm |
| `pve.guest.migrate` | Migrate | L2 | Only if cluster supports it; single node → unsupported |
| `pve.guest.clone` | Clone | L2 | Target storage/name validation |
| `pve.guest.template` | Convert to template | L2 | Confirm; irreversible for that guest |
| `pve.snapshot.create` | Snapshot | L1 | Name, description |
| `pve.snapshot.delete` | Delete snapshot | L3 | Exact snapshot confirmation |
| `pve.snapshot.rollback` | Rollback | L3 | Destructive; step-up |
| `pve.backup.start` | Start backup | L1 | Storage and mode validation |
| `pve.backup.restore` | Restore | L3 | Exact archive + target; step-up |
| `docker.restart` | Restart container | L2 | Health check after |
| `docker.start` / `docker.stop` | Lifecycle | L2 | Confirm |
| `docker.update` | Pull/update image | L2 | Rollback image tag |
| `web.restart` | Restart website | L2 | Health check after |
| `gateway.restart` | Restart gateway | L2 | Self-restart guard |

### 3.3 Destructive (explicit safeguards)

| ID | Operation | Risk | Required safeguards |
|---|---|---|---|
| `pve.guest.destroy` | Delete CT/VM | L3 | Preview, exact ID+name, step-up, typed confirmation, backup warning |
| `pve.disk.remove` | Remove disk | L3 | Exact disk, data-loss warning, step-up |
| `pve.storage.remove` | Remove storage definition | L3 | Exact storage, no active volumes, step-up |
| `pve.backup.delete` | Delete archive | L3 | Exact archive id, size, date, step-up |
| `pve.snapshot.delete` | Delete snapshot | L3 | Exact snapshot, step-up |
| `docker.volume.delete` | Delete volume | L3 | Exact volume, container stopped, step-up |
| `docker.compose.down` | Compose down | L3 | Project scope, volume policy, step-up |
| `pve.user.delete` | Delete user/token | L3 | Exact identity, step-up, audit |
| `pve.firewall.remove` | Remove rule | L3 | Exact rule, lockout warning, step-up |

## 4. Confirmation model

| Risk | Preview | Confirmation | Step-up |
|---|---|---|---|
| L0 read | No | No | No |
| L1 create/start | Yes | Single confirm | No |
| L2 disruptive | Yes | Confirm + impact text | Optional |
| L3 destructive | Yes | Type exact target string | **Yes, within 5 min** |

Step-up reauthentication must be server-validated; a client-side flag is not sufficient.

## 5. Validation rules

- Target IDs must exist in the live inventory.
- Guest name must match the ID if supplied.
- Storage must be active and have enough free space.
- Backup archive must be complete and verified.
- A restore target must not be the running source guest.
- Docker volume deletion requires the container stopped and the volume not in use.
- Firewall changes must include an anti-lockout check.
- No operation accepts a free-text shell, SQL, URL or path.
- All strings are bounded, normalized and rejected on control characters.

## 6. Natural-language command centre

Supported examples:

```text
show all
show pve
show ct 104
show vm 200
show storage health
show backup failures
restart navidrome
start vm 200
run backup ct 104
show last 20 admin actions
delete backup <exact archive identifier>
```

Pipeline:

1. Parse intent into `operation_id` + typed parameters.
2. Resolve targets against the live inventory.
3. If ambiguous, ask a clarifying question; never guess.
4. Show preview with current state and intended change.
5. Apply risk policy and step-up.
6. Execute with an idempotency key.
7. Record audit before and after.
8. Return correlated result to `kf-command-result`.

No plain ntfy message can execute a command. Only an authenticated `kaminari_app` session can create a command request.

## 7. Docker management

Scope: the 56 containers on CT104. Required:

- List and filter by project/health.
- Inspect image, ports, mounts, network, restart policy.
- Show CPU/RAM/network/disk usage.
- Sanitized bounded logs (no secrets).
- Restart/start/stop with health verification.
- Detect unhealthy/restart loops.
- Image/update status.
- Volume inspection; deletion is L3 with enhanced approval.
- Compose project inspect; `down` is L3.

## 8. Storage and backup administration

- Storage inventory and capacity.
- SMART/ZFS status and scrub history.
- Backup archive inventory with size/date/integrity.
- Start backup; verify; restore preparation.
- Retention policy management.
- Approved archive deletion (exact target, size, date).
- Snapshot management.
- No destructive disk tests.
- No restore over a running production guest without explicit confirmation.

## 9. Website and services

- Site availability, `/healthz`, `/api/health`.
- DNS, TLS expiry, Cloudflare Tunnel.
- Express/SQLite health and dependency status.
- Controlled restart and deployment health.
- Website event history.
- All website events publish **only** to `kf-web`.

## 10. Remote access

Use the existing Cloudflare Tunnel; do not expose PVE or SSH directly.

Required:

- HTTPS only.
- MFA/OIDC.
- Short-lived access tokens and rotating refresh tokens.
- Device/session revocation.
- Rate limiting.
- Server-side authorization.
- Audit trail.
- Secure WebSocket/console only if needed, with per-session approval.
- No raw PVE API or root SSH from the public internet.

## 11. Audit model

Each event records:

```json
{
  "event_id": "uuid",
  "timestamp": "ISO-8601",
  "actor": "kaminari_app",
  "session_id": "opaque",
  "operation_id": "pve.guest.stop",
  "target": {"type": "ct", "id": 104, "name": "docker-stack"},
  "parameters_hash": "sha256",
  "risk": "L2",
  "preview": true,
  "confirmation": "typed-target",
  "step_up": true,
  "result": "success|failed|denied",
  "duration_ms": 1234,
  "correlation_id": "uuid"
}
```

Audit events go to `kf-admin-audit` and a local append-only store. Do not include secrets or full command output.

## 12. Implementation stages for Freebuff

1. **Read-only gateway** — auth, OIDC/MFA, read operations, audit, tests.
2. **Reversible operations** — start/stop/reboot, Docker restart, backup start, snapshot create.
3. **Destructive operations** — preview/step-up/exact-target; no real use until owner tests.
4. **Natural-language layer** — typed intent parsing; no free-text execution.
5. **Web console** — same API and authorization.
6. **Android control centre** — same typed operations, no privileged credentials.

## 13. Acceptance criteria

- Every operation is in the catalogue; no ad-hoc PVE calls.
- Read-only token cannot mutate.
- L3 operations require step-up and exact-target confirmation.
- Audit is written before and after execution.
- No PVE root credential in app or gateway.
- No arbitrary shell/SQL/URL execution.
- Docker volume deletion and guest destroy are tested only with explicit owner approval.
- A denied operation returns a clear, audited error.
