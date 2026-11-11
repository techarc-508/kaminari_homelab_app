# Architecture

## Layers

| Layer | Responsibility | Key files |
|---|---|---|
| UI | Compose screens, navigation, theme profiles | `MainActivity.kt`, `ui/screens/*`, `ui/theme/*` |
| Presentation | Screen state, one-shot events, form validation | `MainViewModel.kt`, `PublishViewModel.kt`, `ActivityViewModel.kt`, `CommandViewModel.kt` |
| Domain/repository | Profile, message, outbox, activity and command rules | `data/repository/*` |
| Local data | Room, DataStore, Keystore-backed secret store | `data/local/*`, `data/preferences/*`, `security/*` |
| Network | ntfy stream, publish, health, admin and gateway clients | `data/api/*`, `commands/*` |
| Delivery | Instant foreground service, Battery Saver worker, boot restore, notifications | `delivery/*`, `work/*` |

The UI never opens Room or OkHttp directly. Repositories are constructed in `AppContainer` and passed to ViewModels. Stored credentials are never exposed in UI state.

## Startup and state

`KaminariApp` creates the container. `MainActivity` collects `MainViewModel.uiState`, applies `ThemeSetting.STANDARD` or `ThemeSetting.EINK`, and renders the current destination. `MainViewModel` owns the active profile, topic subscriptions, connection state, inbox filters and navigation. `PublishViewModel`, `ActivityViewModel` and `CommandViewModel` own their screen state and call repositories.

`DataStore<Preferences>` stores non-secret values: active profile ID, delivery mode, theme and sample-data setting. The reconnect cursor is the newest durable message ID in Room (`MessageRepository.latestCursor`), not a DataStore value. `SecretStore` uses an Android Keystore AES/GCM key and stores only the encrypted credential envelope; the plaintext token/password exists only in memory for the request.

## Room v2

`KaminariDatabase` is the source of truth. Version 1 had `server_profiles`, `topic_subscriptions`, `messages`. Version 2 adds:

- `outbox_entries` — durable publish state, attempt count, server message ID, timestamps, retryability.
- `activity_events` — bounded timeline; summaries only, never message bodies.
- `command_requests` — typed command lifecycle, unique nonce, expiry, confirmation, result.

`MIGRATION_1_2` contains only `CREATE TABLE/INDEX IF NOT EXISTS`; there is no destructive fallback. Deduplication is enforced by a unique index on `(server_profile_id, ntfy_message_id)`. All inbox queries are profile-scoped; saved and read state are local flags.

Schema files are exported under `app/schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/` (`1.json`, `2.json`). `Migration1To2Test` opens a real v1 database created from `1.json`, inserts data, runs the migration and verifies existing rows and new tables.

## Network flow

Receive: `NtfyStreamClient` calls the JSON stream `GET {base}/{topics}/json?since=...`, parses one JSON object per line, ignores `keepalive` safely, handles `open`/`message`/`poll_request` and unknown events, and reports malformed lines as bounded diagnostics. `NftyRepository` maps stream results to UI state; `MessageRepository` stores messages and writes one activity event per new message in the same transaction.

Publish: `NtfyPublishClient` builds a documented ntfy publish request, applies authorization from `NtfyAuth`, uses bounded timeouts and does **not** automatically retry. `OutboxRepository` claims a row with conditional SQL, sends it, then records `ACCEPTED`, `FAILED` or `AMBIGUOUS`. A process restart moves orphaned `SENDING` rows to `AMBIGUOUS`; it never resends them automatically.

## Outbox state machine

```
QUEUED ──claim──▶ SENDING ──server 2xx──▶ ACCEPTED
                      ├──definitive failure──▶ FAILED(retryable=false)
                      ├──transient/auth/429/5xx──▶ FAILED(retryable=true)
                      └──timeout/ambiguous──▶ AMBIGUOUS
FAILED(retryable=true) ──manual retry──▶ QUEUED
AMBIGUOUS ──explicit duplicate-risk confirmation──▶ QUEUED
SENDING (orphaned by process death) ──recovery──▶ AMBIGUOUS
```

`ACCEPTED` is a server receipt. It does not prove handset delivery or human read. There is no recipient-ack protocol in v1.

## Delivery

**Instant:** `InstantForegroundService` owns a foreground `dataSync` stream while permitted. Android 15+ limits `dataSync` foreground services; `onTimeout` stops the service before the fatal deadline and migrates to Battery Saver. The service never claims guaranteed delivery.

**Battery Saver:** `BatterySaverWorker` runs periodic WorkManager catch-up. Android enforces a 15-minute minimum and Doze can delay work; the UI says “periodic, inexact”.

**Boot:** `BootReceiver` restores only the delivery preference after `BOOT_COMPLETED` and after first unlock; credential-encrypted storage is unavailable earlier.

## Command envelope

`rplab.command.v1` contains `commandId`, `actionId`, `target`, `args`, `issuedAt`, `expiresAt`, `nonce`, `requestedBy` and `schema`. `CommandPolicy` is a fixed allowlist. Expiry is five minutes. `CommandDao` enforces unique nonces, conditional status transitions and expiry checks. Destructive actions require a second confirmation bound to the nonce.

`MockCommandExecutor` is the only wired executor. It returns `mock: true` and never opens a socket or shell. `GatewayCommandExecutor` is a boundary for a future separately deployed gateway; it is not enabled in the app container.

## Proxmox boundary

The APK never holds Proxmox credentials. The optional FastAPI gateway holds a dedicated `kaminari-gateway@pve` token with `KaminariRead` (audit) permissions and privilege separation. Mutations are disabled by default and require a separate owner decision and server-side policy.
