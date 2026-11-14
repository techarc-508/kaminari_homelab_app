# `kf-` Topic ACL Matrix

**Status:** Design only. **No account, token or ACL was created in Stage 1.**
**Server version:** ntfy **2.28.0** (installed).
**Principle:** topic names are not access control. Every topic is protected by user ACLs and, after migration, `auth-default-access=deny-all`.

## 1. Identities

| Identity | Role | ntfy admin? | Purpose |
|---|---|---|---|
| `kfadmin` | ntfy superadministrator | **Yes** | User/topic/ACL/token administration; web UI; owner only. Never in Android. |
| `kaminari_app` | Application/gateway owner | **No** | Read the notification ecosystem; issue commands; read results/audit. Gateway uses a separate token under this user. |
| `kf_monitor` | Monitoring publisher | **No** | Write infrastructure/web/security/ntfy events. |
| `kf_music` | Music pipeline publisher | **No** | Write music pipeline and report topics. |
| `kf_stickers` | Sticker pipeline publisher | **No** | Write sticker pipeline and report topics. |
| `kf_news` | News publisher | **No** | Write news and news-report topics. |
| `anonymous` | Public default | — | **Denied** after migration. |

`kaminari_app` is **not** an ntfy administrator. It is the owner-level application identity in the **gateway**, not in ntfy. The gateway authorization role is separate and is defined in `KF_ADMIN_AND_GATEWAY_AUTHORIZATION_MODEL.md`.

## 2. Matrix

Legend: **R** = read, **W** = write/publish, **R/W** = both, **—** = denied.

| Topic | `kfadmin` | `kaminari_app` | `kf_monitor` | `kf_music` | `kf_stickers` | `kf_news` | `anonymous` |
|---|---|---|---|---|---|---|---|
| `kf-web` | admin | R | W | — | — | — | — |
| `kf-pve-host` | admin | R | W | — | — | — | — |
| `kf-pve-ct` | admin | R | W | — | — | — | — |
| `kf-pve-vm` | admin | R | W | — | — | — | — |
| `kf-storage` | admin | R | W | — | — | — | — |
| `kf-backups` | admin | R | W | — | — | — | — |
| `kf-network` | admin | R | W | — | — | — | — |
| `kf-services` | admin | R | W | — | — | — | — |
| `kf-security` | admin | R | W | — | — | — | — |
| `kf-ntfy` | admin | R | W | — | — | — | — |
| `kf-digest` | admin | R | W | — | — | — | — |
| `kf-music-pipeline` | admin | R | — | W | — | — | — |
| `kf-music-reports` | admin | R | — | W | — | — | — |
| `kf-stickers-pipeline` | admin | R | — | — | W | — | — |
| `kf-stickers-reports` | admin | R | — | — | W | — | — |
| `kf-news` | admin | R | — | — | — | W | — |
| `kf-news-reports` | admin | R | — | — | — | W | — |
| `kf-command-request` | admin | W | — | — | — | — | — |
| `kf-command-result` | admin | R | — | — | — | — | — |
| `kf-admin-audit` | admin | R/W | — | — | — | — | — |
| `kf-diary` | admin | R/W | — | — | W | — | — |
| `kf-iot` | admin | R | W | — | — | — | — |

The gateway uses a **separate token under `kaminari_app`**; it can read `kf-command-request`, write `kf-command-result` and `kf-admin-audit`. If the owner wants true producer separation, a future `kf_gateway` identity can be added; it is not introduced here to keep the approved six identities unchanged.

## 3. Effective ACL rules

After authentication is enabled:

1. `auth-default-access: deny-all`.
2. `kfadmin` remains the only administrator.
3. `kaminari_app` gets `kf-*` **read-only** plus explicit write on `kf-command-request`, `kf-admin-audit`, and (optionally) `kf-diary`.
4. Producer accounts get **write-only** on their exact topics, not a broad `kf-*` write.
5. No account gets `kf-*` read/write by convenience.
6. Anonymous access is denied after all producers are migrated.

Example intent (not executed):

```bash
# Read everything, write only commands/audit (and optionally diary)
ntfy access kaminari_app 'kf-*' read-only
ntfy access kaminari_app kf-command-request write-only
ntfy access kaminari_app kf-admin-audit write-only

# Monitoring producer: write-only, exact topics
ntfy access kf_monitor kf-web write-only
ntfy access kf_monitor kf-pve-host write-only
# ... same for each infrastructure topic

# Music producer
ntfy access kf_music kf-music-pipeline write-only
ntfy access kf_music kf-music-reports write-only
```

Verify wildcard precedence on the installed 2.28.0 before applying. Do not assume ACL ordering; test an allowed and a denied topic with each token.

## 4. Tokens

| Token label | User | Stored where | Expiry | Notes |
|---|---|---|---|---|
| `Kaminari Android` | `kaminari_app` | Android Keystore-backed store | No automatic expiry if supported | Not an admin token |
| `Kaminari Gateway` | `kaminari_app` | Gateway secret store, not Git | No automatic expiry | Separate from Android |
| `Kaminari Monitor` | `kf_monitor` | Host secret store | No automatic expiry | Write-only |
| `Kaminari Music` | `kf_music` | Music host secret store | No automatic expiry | Write-only |
| `Kaminari Stickers` | `kf_stickers` | Future diary host store | No automatic expiry | Reserved |
| `Kaminari News` | `kf_news` | News host secret store | No automatic expiry | Write-only |

Tokens inherit the user ACLs. A permanent token is only as safe as the user it belongs to. The Android token must never be an admin token.

## 5. Revocation

```bash
# List token labels without printing secrets
ntfy token list --user kaminari_app

# Delete a token by label
ntfy token remove --user kaminari_app 'Kaminari Android'

# Revoke the whole user if compromised
ntfy user remove kaminari_app
```

For `kfadmin`, revoke sessions/tokens and rotate the password. Do not place the admin token in the APK or the gateway.

## 6. Migration gate

The ACL matrix is accepted only when all of these pass:

- `kaminari_app` can read each approved alert topic.
- `kaminari_app` can write only `kf-command-request` (and audit/diary if approved).
- `kf_monitor` can write its exact topics and is denied command/security admin paths.
- `kf_music`, `kf_stickers`, `kf_news` can write only their own topics.
- `anonymous` is denied after migration.
- Android and gateway tokens are distinct and revocable.
- No token secret appears in a report, Git commit, log or APK.
