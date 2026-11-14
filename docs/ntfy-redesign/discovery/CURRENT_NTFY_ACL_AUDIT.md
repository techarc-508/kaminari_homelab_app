# Current ntfy Access-Control Audit

**Discovery date:** 2026-11-11  
**Status:** read-only. No user, token, ACL or `auth-default-access` value was created or changed.

---

## 1. Current access-control mode

| Control | Current state | Evidence |
|---|---|---|
| Authentication | **Disabled** | `/v1/config`: `enable_login=false`, `require_login=false` |
| Users | **0** | server stats `users=0`; no auth database |
| Admin account | None | no user database |
| Access tokens | None | no token database |
| Per-topic ACLs | None | no auth database |
| Anonymous read | **Allowed** | unauthenticated `/v1/stats` works; topics are readable |
| Anonymous publish | **Allowed** | PVE, dashboard and Docker watcher all publish without credentials |
| Sign-up | Disabled | `/v1/config` |
| Login page | Disabled | `/v1/config` |
| `auth-default-access` | Not configured; server is effectively open | no `server.yml`, no auth file |

The current server has **no identity layer**. Every producer and consumer is anonymous. The only thing separating one topic from another is the topic name. Topic names are visible in PVE configuration, container environments, the dashboard source and likely logs; they are not secrets.

---

## 2. Effective permission matrix (today)

| Principal | Read any topic? | Write any topic? | Admin? | Notes |
|---|---|---|---|---|
| Unauthenticated network client | **Yes** | **Yes** | No | Public endpoint through Cloudflare |
| PVE webhook | Yes | Yes | No | Anonymous HTTP publish |
| RPLab dashboard | Yes | Yes | No | Anonymous |
| Docker events watcher | Yes | Yes | No | Anonymous |
| Android app | Yes | Yes | No | Guest mode |
| `kaminari-diary` | Yes | Yes | No | Anonymous |
| Owner/admin | Not distinguishable from anonymous | Not distinguishable | No admin account exists | No audit trail of who published/read |

This is the core security problem: the server cannot distinguish the owner from a stranger, cannot revoke one producer without changing the topic, and cannot audit administrative actions.

---

## 3. What the current state can and cannot prove

### Proven

- Anonymous access is enabled (`enable_login=false`, `require_login=false`, `users=0`); by server configuration, a network client can read and publish to any non-disallowed topic without credentials. No message body was read during this audit.
- PVE publishes to `KAMINARI-FUJI-Homelab` anonymously; the task log records an HTTP 400 for one backup notification.
- No auth database exists, so no user ACL can be enforced.
- No token exists, so no token revocation is possible today.

### Not proven

- That every possible topic is readable by a stranger (there may be topics with no recent messages; topic names not enumerable).
- That the PVE webhook 400 is caused by authentication (it is not: auth is disabled).
- That Cloudflare Access/WAF protects the ntfy hostname; the origin is reachable through the tunnel and the app uses it anonymously. Cloudflare-side rules were not enumerated.
- That the LAN origin is inaccessible from untrusted networks; the host firewall is disabled and the service is reachable on the LAN.

---

## 4. Security impact by scenario

| Scenario | Impact today |
|---|---|
| Stranger learns `KAMINARI-FUJI-Homelab` | Can read homelab alerts, backup failures, website events; can publish fake alerts or spam |
| Stranger learns `kaminari-diary` | Can read/write personal diary notifications |
| Compromised Android device | Can read/write every topic; no per-topic revocation; token does not exist yet |
| PVE backup failure | Alert may not arrive (HTTP 400) and the failure is silent except in task history |
| Rogue Docker container | Can publish to the unified topic if it learns the URL/topic; no per-container identity |
| Logs/backups leak | Topic names and message content may be exposed; no encryption at the ntfy layer |
| Incident response | No audit trail of who published or read; no user to disable |

---

## 5. Why enabling auth is not a one-line change

Every current producer is anonymous:

1. PVE webhook `kaminari-ntfy`
2. `rplab-dashboard` (`kaminari-fuji-gate`)
3. `kaminari-docker-events`
4. `kaminari-diary`
5. MySpeed integration
6. Request bot
7. Android app
8. Any unknown consumer (Uptime Kuma, Duplicati, manual clients)

If `auth-default-access=deny-all` is enabled before all producers have identity and ACLs, **backup alerts, website alerts, Docker events, diary notifications and the app will silently stop**. That is an unacceptable production risk.

**Required order:**

1. Back up the current state (there is no auth DB yet; document the empty state).
2. Create `kfadmin`, `kaminari_app`, `rplab_monitor` (or per-producer users).
3. Create topics and grant explicit ACLs.
4. Issue tokens; update each producer one at a time.
5. Verify each producer with a safe, dedicated test topic.
6. Verify the Android app with its token and unauthorized-topic denial.
7. Only then change `auth-default-access` to `deny-all`.
8. Keep a documented rollback: restore the previous config and tokens; re-enable anonymous access temporarily if a critical producer fails.

No step in this list is executed in Phase 0.

---

## 6. Proposed identity separation

| Identity | Role | Privileges | Secret storage |
|---|---|---|---|
| `kfadmin` | ntfy administrator | Full admin; user/token/ACL management; web UI use only | Owner private file; never in Android |
| `kaminari_app` | Normal restricted user | Read `rplab-*` alert topics; write `rplab-command-request` and temporary test topic | Android Keystore-backed storage; never in repo/chat |
| `rplab_monitor` | Normal restricted user | Write `rplab-*` alert topics; no admin | Private service environment/secret store |
| Optional `pve_notify`, `web_notify`, `backup_notify` | Normal restricted users | Write only their domain topic | Private service files |

**Token rule:** an ntfy access token inherits the permissions of its user. A permanent token is only as safe as the user’s ACL. Never create an admin token for the Android app.

---

## 7. ACL design constraints to verify on 2.28.0

- Exact syntax and precedence of `ntfy access` and `auth-default-access` must be verified against the installed **2.28.0** before use; the official documentation describes current releases and may include newer behavior.
- Test wildcard behavior explicitly; do not assume `rplab-*` matches as expected without a test.
- Test user-specific ACL overriding `everyone` and default-deny.
- Test read-only denial on a write attempt and write-only denial on a read attempt.
- Test token revocation and confirm the token stops working promptly.
- Test that an admin token is not accepted by the Android app configuration (the app should not have a field for it).

---

## 8. Findings and severity

| ID | Severity | Finding |
|---|---|---|
| ACL-01 | **Critical** | No authentication; anyone can read/write known topics |
| ACL-02 | **Critical** | No per-producer identity; one leaked topic name exposes all messages on that topic |
| ACL-03 | **High** | No admin account; no revocation or audit path |
| ACL-04 | **High** | Auth migration can break every producer if default-deny is applied too early |
| ACL-05 | **Medium** | Topic names are treated as secrets in existing documentation; they are not |
| ACL-06 | **Medium** | No token expiry/rotation policy exists because no tokens exist |
| ACL-07 | **Medium** | PVE webhook notification is already failing with HTTP 400, so alert delivery is not trustworthy |

---

## 9. Recommendation

Approve the three-account model and the staged migration in `PROPOSED_NTFY_ACCESS_CONTROL_MODEL.md`. Do not create `kfadmin` or `kaminari_app` during discovery. Do not change `auth-default-access` until a successful producer migration and rollback have been demonstrated.
