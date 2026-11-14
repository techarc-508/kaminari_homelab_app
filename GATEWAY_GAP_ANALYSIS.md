# Gateway Gap Analysis — Freebuff Phase 0

**Target:** `KAMINARI_LEVEL4_CONTROL_PLANE_SPEC.md` and `KF_ADMIN_AND_GATEWAY_AUTHORIZATION_MODEL.md`
**Current code:** `gateway/app/` (FastAPI), 9 tests
**Deployment status:** **not deployed**; no chosen always-on host; no TLS endpoint
**Baseline:** `PYTHONPATH=. python3 -m pytest -q` → 9 passed, 1 warning (2026-11-12)

---

## 1. What exists today

### 1.1 HTTP surface

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /healthz` | none | liveness |
| `GET /readyz` | bearer | PVE reachability |
| `GET /v1/proxmox/nodes` | bearer | node list |
| `GET /v1/proxmox/qemu?node=` | bearer | VM list |
| `GET /v1/proxmox/lxc?node=` | bearer | CT list |
| `GET /v1/proxmox/status?node=&kind=&vmid=` | bearer | guest status |
| `GET /v1/proxmox/storage?node=` | bearer | storage list |
| `GET /v1/proxmox/tasks?node=` | bearer | task list |
| `POST /v1/commands/execute` | bearer | typed command envelope |

> Note: the REST `/v1/proxmox/*` routes are **untyped passthrough endpoints** in the sense that they accept arbitrary `node` (regex-validated) and return raw PVE payloads. They are not part of the typed operation catalogue, which the Level 4 spec requires ("no ad-hoc PVE calls"). They bypass the command envelope, audit, risk classification and approval model.

### 1.2 Command catalogue

| Action | Type | Implemented |
|---|---|---|
| `host.health` | read | Yes |
| `node.list` | read | Yes |
| `vm.list` | read | Yes |
| `container.list` | read | Yes |
| `vm.status` | read | Yes |
| `container.status` | read | Yes |
| `storage.summary` | read | Yes |
| `task.history` | read | Yes |
| `vm.start` | mutation | Yes |
| `vm.stop` | mutation | Yes |
| `vm.restart` | mutation | Yes |
| `container.start` | mutation | Yes |
| `container.stop` | mutation | Yes |
| `container.restart` | mutation | Yes |

### 1.3 Security controls present

- Static bearer token, constant-time compare.
- Per-token-prefix sliding-window rate limit (default 60/min).
- Request body size limit (64 KiB default).
- Envelope schema, UUID, nonce shape, 5-minute window, 60-second issue skew, expiry, target, action allowlist, `requested_by` shape, `requires_confirmation` consistency.
- Mutations disabled by default.
- In-memory replay cache.
- Structured audit log line with token/secret keys filtered.
- PVE token stays server-side.

---

## 2. Gap against the Level 4 specification

### 2.1 Read / overview operations

| Spec ID | Required | Status |
|---|---|---|
| `pve.node.list` | Node inventory | **Partial** (`node.list` exists; `/v1/proxmox/nodes` also) |
| `pve.node.status` | CPU/RAM/disk/network/uptime | **Missing** (no `/nodes/{node}/status`) |
| `pve.guest.list` | CT/VM inventory | **Implemented** (`vm.list`, `container.list`) |
| `pve.guest.inspect` | Guest config, agent, disks | **Missing** |
| `pve.storage.list` | Capacity/health | **Partial** (`storage.summary` returns storage list, not usage/health detail) |
| `pve.storage.inspect` | ZFS/pool/SMART summary | **Missing** |
| `pve.task.list` | Recent tasks | **Partial** (`task.history`) |
| `pve.task.log` | Sanitized task log | **Missing** |
| `pve.network.list` | Bridges/interfaces | **Missing** |
| `pve.firewall.list` | Rules/status | **Missing** |
| `pve.user.list` | Accounts/roles/tokens (no secrets) | **Missing** |
| `pve.update.status` | Package/update state | **Missing** |
| `docker.list` | Containers | **Missing** |
| `docker.inspect` | Config/health/resources | **Missing** |
| `docker.logs` | Sanitized bounded logs | **Missing** |
| `web.health` | Site/API/TLS/tunnel | **Missing** |
| `ntfy.health` | Health/version/auth mode | **Missing** |
| Backup status / retention detail | Backups + retention | **Missing** |
| Monitoring-system health | Uptime Kuma, Beszel, Scrutiny, InfluxDB, Healthchecks, CrowdSec, Dozzle, Apprise | **Missing** |
| Pipeline activity (music/news/stickers) | Read from ntfy or pipeline API | **Missing** |

### 2.2 Lifecycle operations

| Spec ID | Risk | Status |
|---|---|---|
| `pve.guest.start` | L1 | **Implemented** |
| `pve.guest.shutdown` | L2 | **Missing** (only forced `stop`) |
| `pve.guest.stop` | L2 | **Implemented** |
| `pve.guest.reboot` | L2 | **Implemented** |
| `pve.guest.migrate` | L2 | **Missing** (single node; document unsupported) |
| `pve.guest.clone` | L2 | **Missing** |
| `pve.guest.template` | L2 | **Missing** |
| `pve.snapshot.create` | L1 | **Missing** |
| `pve.snapshot.delete` | L3 | **Missing** |
| `pve.snapshot.rollback` | L3 | **Missing** |
| `pve.backup.start` | L1 | **Missing** |
| `pve.backup.restore` | L3 | **Missing** |
| `docker.restart` | L2 | **Missing** |
| `docker.start` / `docker.stop` | L2 | **Missing** |
| `docker.update` | L2 | **Missing** |
| `web.restart` | L2 | **Missing** |
| `gateway.restart` | L2 | **Missing** |

### 2.3 Destructive operations

| Spec ID | Status |
|---|---|
| `pve.guest.destroy` | **Missing** |
| `pve.disk.remove` | **Missing** |
| `pve.storage.remove` | **Missing** |
| `pve.backup.delete` | **Missing** |
| `pve.snapshot.delete` | **Missing** |
| `docker.volume.delete` | **Missing** |
| `docker.compose.down` | **Missing** |
| `pve.user.delete` | **Missing** |
| `pve.firewall.remove` | **Missing** |

**Conclusion:** the implemented surface is roughly the first slice of the Level 4 read catalogue plus six reversible guest operations. The destructive catalogue, Docker, storage administration, backup, network/firewall, user administration, web and monitoring reads are entirely unimplemented.

---

## 3. Security-control gaps

| Control required by spec | Current state | Gap |
|---|---|---|
| OIDC/MFA login | none; one static bearer token | **Missing** |
| Short-lived access tokens (15 min) | static token, no expiry | **Missing** |
| Rotating refresh tokens | none | **Missing** |
| Device/session revocation | none | **Missing** |
| Per-actor authorization (owner/operator/viewer/service) | none; one role | **Missing** |
| Server-side authorization on every route | yes, but all-or-nothing | **Partial** |
| Rate limiting per identity | keyed by token prefix; one shared identity | **Weak** |
| Lockout after failures | none | **Missing** |
| CSRF protection for browser sessions | no browser session exists | **N/A now, required for web** |
| Step-up reauthentication (5 min) | `approval_token == nonce` supplied by the client | **Not real step-up** |
| Exact-target confirmation | client-side only; server does not compare a typed target string | **Missing** |
| Typed operation ID from a fixed catalogue | yes for `/v1/commands/execute`; no for REST passthrough | **Partial** |
| Target exists in live inventory | regex only | **Missing** |
| Preview with current state | not returned | **Missing** |
| Risk classification L0–L3 | single boolean `mutations_enabled` | **Missing** |
| Idempotency key | nonce replay cache only, in memory | **Partial / volatile** |
| Audit before execution | log line after receipt, no pre-execution record | **Partial** |
| Audit after execution | log line | **Partial** |
| Durable append-only audit | none | **Missing** |
| Audit to `kf-admin-audit` | none | **Missing** |
| Result to `kf-command-result` | none; HTTP response only | **Missing** |
| No raw PVE passthrough | `/v1/proxmox/*` returns raw payloads | **Gap vs spec intent** |
| No shell/SSH/SQL/URL execution | satisfied | **Met** |
| No PVE root credential | satisfied; least-privilege token | **Met** |
| Gateway token/secret handling | env only, redaction helper | **Met (basic)** |
| HTTPS enforcement in-process | none; relies on reverse proxy | **Deployment-dependent** |
| `/healthz` and `/readyz` only anonymous | satisfied | **Met** |
| Self-restart guard | none | **Missing** |

### 3.1 Specific unsafe behaviours

1. **Self-asserted approval.** For a mutation, the client sends `approval_token` equal to the nonce it generated. Possession of the shared bearer token is sufficient to mutate. There is no second factor and no independent authorization decision.
2. **REST bypass.** `GET /v1/proxmox/*` is authenticated but not typed, not risk-classified and not audited beyond the request. The spec says every PVE call must be an operation in the catalogue.
3. **Global mutation switch.** Enabling mutations enables all six actions at once; there is no per-operation enablement or risk tier.
4. **Volatile replay protection.** A restart clears `ReplayCache`, so a captured envelope can be replayed after restart within its 5-minute validity.
5. **Docker/monitoring not reachable.** The Level 4 promise ("56 containers, storage, backups, security") is far beyond the current gateway.

---

## 4. Test-coverage gaps

Existing 9 tests:

1. `/healthz` unauthenticated
2. `/v1/proxmox/nodes` requires auth
3. authenticated nodes
4. `host.health`
5. replay rejected
6. mutation requires approval
7. mutation with approval
8. invalid vmid
9. rate limit

Missing tests required by Phase 2:

- wrong token / malformed Authorization / empty token
- expired envelope; issued_at outside skew; window > 5 minutes; `expires_at <= issued_at`
- unknown action; action not in allowlist
- target mismatch; malformed nonce; non-UUID command id
- `requires_confirmation` mismatch
- node/vmid injection and boundary lengths
- replay after process restart (currently impossible to pass with the in-memory cache)
- rate limit per identity, not per shared-token prefix
- request size limit
- mutation disabled path
- approval token invalid/absent/wrong
- audit record content and secret redaction
- no raw PVE error leakage
- result publishing to `kf-command-result`
- deny paths for viewer/operator roles (once roles exist)

---

## 5. Data-model / contract gaps

| Item | Current | Required |
|---|---|---|
| Command schema | `rplab.command.v1` | Keep the versioned schema, but align with `kf-command-*` topics and the approved action IDs |
| Reply topic | client constant `rplab-command-results`; gateway ignores it | `kf-command-result` |
| Action IDs | gateway set differs from Android set | One shared catalogue, generated or duplicated with a contract test |
| Risk level | not in the envelope or response | Required field for UI preview and server policy |
| Preview | absent | Operation returns current state + intended change before confirmation |
| Audit reference | absent | `kf-admin-audit` event ID returned with the result |
| Error model | FastAPI `detail` strings | Typed error codes the app can render |
| Idempotency | nonce only | Persisted idempotency key with result reuse |
| Operation metadata | implicit in `if/elif` | Table: id, params, privilege, risk, validation, preview, rollback, audit fields |

---

## 6. Deployment gap

| Requirement | State |
|---|---|
| Chosen always-on host | **None.** VM200 (Mint) is a candidate but not always-on |
| TLS endpoint | none |
| systemd unit | template only, not installed |
| Reverse proxy | example only |
| Secret storage | env template only |
| Health monitoring | not wired to `kf-ntfy` |
| Backup of gateway config | scripts exist, not executed |
| PVE write token | not created; read-only token only |

---

## 7. Required work by phase

### Phase 2 — Gateway hardening (no production changes)

1. Replace the single shared token with the approved identity model. **Blocked by P-02.**
2. Put **every** route through the typed operation layer; remove or explicitly de-scope the raw `/v1/proxmox/*` passthrough.
3. Add risk levels, preview, exact-target confirmation and a real step-up mechanism (P-06).
4. Persist replay/idempotency and audit locally (P-05).
5. Publish result and audit events to `kf-command-result` / `kf-admin-audit` when topics exist (Phase 4/5).
6. Add the missing tests in §4.
7. Add typed error codes and a stable response schema.

### Phase 3 — Application integration

1. Wire the Android `GatewayCommandExecutor` behind an explicit, validated gateway profile.
2. Align the Android and gateway action catalogs.
3. Add preview/confirmation/result UI against the real gateway contract, still testable with mocks.
4. Keep mock execution as the default until the gateway is deployed and verified.

### Phase 4 — Read expansion

1. Node status, guest inspect, storage inspect, task log, network, firewall read, update status.
2. Docker list/inspect/logs (bounded, sanitized).
3. Web/ntfy health, backup status, monitoring stack health.
4. Pipeline activity reads.

### Phase 5/later — Controlled mutations

1. Add write scope to the PVE token only operation-by-operation.
2. Add snapshots, backups, Docker lifecycle, then destructive operations with full safeguards.
3. Do not enable any destructive operation before P-05/P-06 are implemented and tested.

---

## 8. Verdict

The gateway is a **credible read-only prototype**, not a Level 4 control plane. It should not be deployed as-is for owner administration, and mutations must remain disabled until:

- the token/identity split is decided (P-02),
- step-up and exact-target confirmation are real (P-06),
- replay/idempotency and audit are durable (P-05),
- every route is typed and risk-classified,
- and the test gaps above are closed.

No code was changed in this phase.
