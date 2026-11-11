# Configuration reference

## App settings

| Setting | Default | Meaning |
|---|---|---|
| Server URL | `https://ntfy.rplab.qzz.io` | ntfy base URL; must include `http://` or `https://` |
| Auth method | `GUEST` | Guest, Username & Password, Access Token |
| Username | empty | Basic auth only |
| Password/token | empty | Secret; Keystore-encrypted |
| Enforce HTTPS | `true` | Rejects plaintext `http://` |
| Delivery mode | `BATTERY_SAVER` | Instant or Battery Saver |
| Theme | `STANDARD` | Standard or E-Ink |
| Sample data | off | Demo rows, clearly labelled; never mixed with live data |
| Initial stream window | 12 h | Used when no persisted cursor exists; later reconnects use the newest Room message ID |

## Room schema v2

| Table | Purpose |
|---|---|
| `server_profiles` | profile identity, URL, auth method, timestamps |
| `topic_subscriptions` | profile-scoped topic, pause, priority threshold |
| `messages` | message body, metadata, saved/read flags, unique `(profile_id, ntfy_id)` |
| `outbox_entries` | publish state, attempts, retryability, server id |
| `activity_events` | bounded timeline summaries |
| `command_requests` | typed command envelope lifecycle |

Retention:

- Activity: 90 days / 5,000 events per profile.
- Outbox: 30 days / 500 terminal entries per profile.
- Never prune `QUEUED`/`SENDING`; never prune `SUBMITTED` commands.

## Live server

| Item | Value |
|---|---|
| Canonical URL | `https://ntfy.rplab.qzz.io` |
| LAN origin | `http://192.168.0.104:8201` |
| Version | ntfy 2.28.0 |
| Auth | disabled (`users=0`) |
| Existing topic | `KAMINARI-FUJI-Homelab` |
| Test topic | `kaminari-onboarding-test` |

## Gateway environment

| Variable | Required | Default |
|---|---|---|
| `GATEWAY_GATEWAY_TOKEN` | yes | — |
| `GATEWAY_PROXMOX_BASE_URL` | yes | — |
| `GATEWAY_PROXMOX_API_TOKEN` | yes | — |
| `GATEWAY_PROXMOX_VERIFY_TLS` | no | `true` |
| `GATEWAY_PROXMOX_TIMEOUT_SECONDS` | no | `10` |
| `GATEWAY_MUTATIONS_ENABLED` | no | `false` |
| `GATEWAY_RATE_LIMIT_PER_MINUTE` | no | `60` |
| `GATEWAY_MAX_REQUEST_BYTES` | no | `65536` |

## Build environment

| Variable | Purpose |
|---|---|
| `KEYSTORE_PATH` | test-only release keystore path |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |
| `JAVA_HOME` | JDK 21 |
| `ANDROID_HOME` | Android SDK root |

Never commit these values.
