# Proposed ntfy Access-Control Model

**Status:** proposal only. **No account, token, ACL or `auth-default-access` value was created or changed.**  
**Server:** ntfy 2.28.0; current authentication is disabled.

---

## 1. Goals

1. Remove anonymous write access without breaking existing producers.
2. Give every producer and consumer a distinct, revocable identity.
3. Keep the Android app non-admin and topic-restricted.
4. Keep the ntfy administrator out of the APK and out of normal automation.
5. Make the migration reversible and testable.

---

## 2. Proposed identities

| Identity | Role | Purpose | Token? | Where the secret lives |
|---|---|---|---|---|
| `kfadmin` | **Admin** | ntfy user/token/ACL administration; web UI only | No token for app; optional admin token for CLI only | Owner private file, mode 600 |
| `kaminari_app` | **Normal user** | Android app read/write | One token labelled `Kaminari Nfty Android`, no expiry if supported | Android Keystore-backed storage; private onboarding file |
| `rplab_monitor` | **Normal user** | Infrastructure producers write alert topics | One token per producer environment, or one shared monitor token initially | Private service env/secret store |
| `website_notify` | **Normal user** (optional) | Website writes `rplab-web` only | Dedicated token | Website secret env |
| `backup_notify` | **Normal user** (optional) | Backup jobs write `rplab-backups` only | Dedicated token | Backup script secret |
| `diary_notify` | **Normal user** (optional) | `kaminari-diary` writes only its personal topic | Dedicated token | Diary container secret |
| `gateway_exec` | **Normal user** | Gateway reads command requests, writes results | Dedicated token | Gateway secret store |

**Start with three accounts** (`kfadmin`, `kaminari_app`, `rplab_monitor`) and split producers later if the blast radius justifies the extra management. Do not give any of them admin except `kfadmin`.

---

## 3. ACL matrix

Permissions are **per topic**, not per token. A token inherits its user’s ACLs.

### `kaminari_app` — Android

| Topic | Permission | Reason |
|---|---|---|
| `rplab-web` | read-only | Website alerts |
| `rplab-pve-host` | read-only | Host alerts |
| `rplab-pve-ct` | read-only | LXC alerts |
| `rplab-pve-vm` | read-only | VM alerts |
| `rplab-storage` | read-only | Storage alerts |
| `rplab-backups` | read-only | Backup alerts |
| `rplab-network` | read-only | Network alerts |
| `rplab-services` | read-only | Service alerts |
| `rplab-security` | read-only | Security alerts |
| `rplab-ntfy` | read-only | ntfy health |
| `rplab-digest` | read-only | Daily digest |
| `rplab-command-result` | read-only | Command results |
| `rplab-command-request` | **write-only** | Submit typed commands |
| `kaminari-onboarding-test` | read/write (temporary) | Onboarding test only; retire/restrict after migration |
| everything else | **deny** | Default deny |

### `rplab_monitor` — infrastructure producers

| Topic | Permission |
|---|---|
| `rplab-web` | write-only |
| `rplab-pve-host` | write-only |
| `rplab-pve-ct` | write-only |
| `rplab-pve-vm` | write-only |
| `rplab-storage` | write-only |
| `rplab-backups` | write-only |
| `rplab-network` | write-only |
| `rplab-services` | write-only |
| `rplab-security` | write-only |
| `rplab-ntfy` | write-only |
| `rplab-digest` | write-only |
| everything else | **deny** |

The monitor identity does not need read access for normal operation. If a producer needs read-back verification, grant read only on its own topic.

### Optional per-producer identities

| Identity | Write | Read |
|---|---|---|
| `website_notify` | `rplab-web` | none |
| `backup_notify` | `rplab-backups` | none |
| `diary_notify` | `kaminari-diary` | optional own topic |
| `gateway_exec` | `rplab-command-result` | `rplab-command-request` |

### `kfadmin`

Full administrator. No topic ACL is required because admin bypasses topic permissions. **Do not create an Android token for this account.**

---

## 4. Migration sequence (requires owner approval; not executed)

### Step 0 — Backup

- Back up the current config/auth state; today there is no auth DB, so record that fact.
- Back up the Docker volume and the PVE notification config before changing producers.
- Keep a rollback copy outside the `safedrive` pool if possible.

### Step 1 — Create accounts

Use the installed `ntfy` 2.28.0 CLI. Verify syntax with `ntfy user --help`, `ntfy access --help`, `ntfy token --help` before running. Example shape (placeholders, no real password):

```text
ntfy user add --role=admin kfadmin
ntfy user add kaminari_app
ntfy user add rplab_monitor
```

The admin password must be random and stored only in the owner private file. Do not reuse any previously exposed password.

### Step 2 — Create topic ACLs

Grant explicit topics; avoid wildcard assumptions until tested. Example shape:

```text
ntfy access kaminari_app rplab-web read-only
ntfy access kaminari_app rplab-pve-host read-only
...
ntfy access kaminari_app rplab-command-request write-only
ntfy access rplab_monitor rplab-web write-only
...
```

Verify wildcard behavior separately if you choose `rplab-*`; do not assume it matches every topic.

### Step 3 — Issue tokens

```text
ntfy token add --label "Kaminari Nfty Android" kaminari_app
ntfy token add --label "RPLab monitor" rplab_monitor
```

- Omit `--expires` only if 2.28.0 supports non-expiring tokens as documented.
- Never issue an admin token for the app.
- Store the token in the Android Keystore-backed store and in the private onboarding file; never in Git/chat.

### Step 4 — Migrate producers one at a time

Order:

1. `rplab_monitor` test publish to `kaminari-onboarding-test` or a temporary test topic.
2. PVE webhook `kaminari-ntfy` → new topic + token.
3. Website `rplab-dashboard` → `rplab-web`.
4. Docker event watcher → `rplab-services`.
5. Backup job → `rplab-backups`.
6. Diary → its own topic/user.
7. Android app → `kaminari_app` token.
8. Any unknown consumer (Uptime Kuma, Duplicati).

After each producer, verify:

- publish returns 200;
- the intended subscriber receives it;
- an unauthorized topic returns 401/403;
- a wrong token is rejected;
- the old path still works until cutover.

### Step 5 — Enable default deny

Only after every producer is migrated and tested:

```text
# conceptual; exact 2.28.0 syntax must be verified
auth-default-access: deny-all
```

Restart/reload as documented for 2.28.0. Then re-test every producer and the app.

### Step 6 — Verify denial

- `kaminari_app` cannot publish to an alert topic.
- `rplab_monitor` cannot read alert topics.
- A random token is rejected.
- Anonymous access is denied (after cutover).
- `kfadmin` can administer, but no app uses it.

### Step 7 — Revocation test

```text
ntfy token list
ntfy token remove <token-id>
```

Confirm the revoked token stops working. Document the exact command and result.

---

## 5. Rollback

If a critical producer fails after default-deny:

1. Re-enable the previous access mode temporarily.
2. Restore the backed-up config/auth DB.
3. Restart ntfy if required.
4. Re-test the failed producer.
5. Do not leave anonymous access enabled longer than necessary; record the incident.

The rollback must be written and tested before the default-deny change, not improvised during an outage.

---

## 6. Security rules

- Never put `kfadmin` credentials or an admin token in the Android app.
- Never put PVE root credentials in the app, gateway or notification body.
- Never use a topic name as the only protection.
- Never grant `kaminari_app` admin.
- Never share one token across unrelated producers if a per-producer identity is feasible.
- Never publish secrets in notification bodies; use a private link or a local file.
- Rotate any token that appears in chat, logs or a public repository.
- Treat the old exposed passwords as compromised; do not reuse them.

---

## 7. Version-specific cautions

The installed server is **2.28.0**, while the official docs describe current releases. Before implementation, verify on 2.28.0:

- `ntfy user add --role=admin` syntax;
- `ntfy access` syntax and wildcard behavior;
- `ntfy token add` no-expiry default and `--label`;
- `auth-default-access` values and reload/restart behavior;
- whether `everyone`/anonymous rules interact with explicit user ACLs as expected.

Do not run these commands in Phase 0. This document is a proposal, not an execution log.

---

## 8. Approval

Approve:

1. `kfadmin` as the only admin.
2. `kaminari_app` as a non-admin, read-mostly user with write only to command-request/test.
3. `rplab_monitor` as the initial producer identity.
4. The staged migration and rollback above.
5. The decision to keep anonymous access until every producer is migrated.

No account is created until this is approved.
