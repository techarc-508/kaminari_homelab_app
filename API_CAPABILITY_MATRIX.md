# API_CAPABILITY_MATRIX.md — ntfy HTTP surface vs. the prototype (read-only audit)

Facts sourced: official docs (`docs.ntfy.sh/subscribe/api`, `/publish`, `/config`, `/releases`), fetched live this audit; current stable server = **v2.37.2 (2026-11-03)**. Owner's server version is **UNVERIFIED** (decision D3 in AUDIT_REPORT) — matrix marks version-dependent rows.

## 1. Endpoints used by the prototype today

| # | Call site | Method/URL | Query | Auth | Result mapping (code) |
|---|---|---|---|---|---|
| 1 | `testConnection` | GET `/v1/health` (+root fallback `/` on 404) | – | Basic/Bearer/none | 200/204 → AUTHENTICATED if tokenOrPassword non-blank else GUEST (`NftyStreamClient.kt:66-72,97-102`); overclaims auth |
| 2 | `openJsonStream` | GET `/{t1,t2}/json` | `since=12h` fixed | same | 200 → stream; 401/403→AUTH/FORBIDDEN; else UNREACHABLE retryable (`:128-170`) |
| 3 | Publish/admin/anything else | **none exists** | | | grep-verified: 0 POST/PUT bodies |

## 2. Receive-side capabilities documented vs. supported

Source: `subscribe/api` docs (JSON-stream format table incl. fields `id, time, expires, event, topic, sequence_id, message, title, tags, priority(1-5), click, attachment{name,url,type,size,expires}, actions[]`; events `open, keepalive, message, message_delete, message_clear, poll_request`).

| Capability | Documented | Prototype | Gap note |
|---|---|---|---|
| `/{topic}/json` long-lived stream | yes | **yes** (`openJsonStream`) | only `/json`; no sse/raw/ws |
| `since=` duration / unix time / message-id (v2.31+ note: same-second dupes on reconnect, clients must dedupe) | yes | partial (only the fixed string `12h`; no durable cursor) | **hard 12h cap: older history unreachable in-app** |
| `poll=1` / `scheduled=1` | yes | no | needed for Battery Saver phase |
| Multiple topics comma-separated in one stream (max 150 topics v2.33+) | yes | **yes** (`joinedTopics`) | good |
| Per-message filters (title, priority, tags, id, message) | yes | no | n/a |
| Attachment download | yes | partially wired (info-only; browser) | fine for now |
| `poll_request` events (upstream/FCM abstain) | yes | **silently ignored** (`parseJsonLine:256`) | harmless until UnifiedPush/iOS-style push flows matter |
| `message_delete` / `message_clear` events (new in 2026 docs) | yes | **silently ignored** | must handle by Phase 1 |
| Keepalive cadence server ~45 s | yes | handles keepalive → heartbeat tick | ok |
| Mark-as-read / clear / delete cached messages (documented GET-based) | yes | no | relates to E2E-reporting in §6 |

## 3. Publish-side matrix (docs `publish/`)

Publish features `POST/PUT /{topic}`, JSON envelope, headers X-Title/Priority/Tags/Click/Attach/Filename/Icon/Email/Actions/Delay/Markdown, GET webhook, cache: no, message-size-limit default 4K (v2.28 tags ≤ 512 B), etc.

| Feature | Documented-server | Prototype | Server dependency |
|---|---|---|---|
| Simple POST publish | yes | **no code** | – |
| Auth publish (Basic/Bearer) | yes | none (would join §1 row 3) | token from Settings |
| Scheduled/delayed publish | yes | no | server v2.28+ tz-aware scheduling |
| Attachment upload (PUT file) or external URL | yes | no | server disk/bandwidth quota |
| Actions (buttons in notifications) | yes | **render-side only** (`NotificationDetailScreen.kt:368-385`); publishing actions is NOT supported | arrives via stream |
| Email/call broadcast | yes | no | server needs smtp/twilio config (owner-set) |
| Mark-read/clear/delete cached messages | yes | no | needed if history should be cleaned from the client |

## 4. User/account/admin surface (docs `config/`)

| Route | Documented | Access | Prototype |
|---|---|---|---|
| `/v1/health`, `/v1/ready` | yes (health long-standing; ready for load balancers) | open | health only |
| `/metrics`, `/debug/pprof` | yes (opt-in) | opt-in/secure | no |
| **User/ACL/token/tier management via undocumented internal HTTP API** ("Admin API (internal)" in v2.36 release notes) | **no** (docs route all user/ACL/token management to CLI or config-provisioning) | server-admin | no — AGENTS §6 forbids normative dependence |
| admin-socket (Unix, experimental, **no credentials, off by default**) | v2.36+ | local filesystem | no |

**Conclusion:** in-app ntfy administration must be built (if at all) on either the owner-run CLI path or version-gated experimental admin endpoints with explicitly approved risk. Phase 3 candidates; not in Phase 1.

## 5. Android delivery options (per docs + Android reality)

| Path | Feasibility | Where implemented |
|---|---|---|
| **Foreground service + persistent JSON stream** ("Instant") | native; shows an FGS notification; battery cost background-relevant | Phase-2; `foregroundServiceType` required from targetSdk 34 onward — `dataSync` works but Android 15+ budgets it (6 h per 24 h), so the robust choice for always-on home-lab streaming is `specialUse` with a documented rationale (fine for a locally built APK) |
| **WorkManager periodic poll** ("Battery Saver") | works with `?poll=1&since=<lastId>`; latency = the period (minutes) | Phase-2; UI wording must honestly say "minutes" |
| **UnifiedPush** (`up*` topics + subscriber-based rate limiting) | for app-server push apps | not applicable to this client; documented server config `visitor-subscriber-rate-limiting` |
| **FCM** (server→device push w/o FGS) | requires **custom-built APK + `firebase-key-file` on the server + Firebase init in the app** (docs `config/` §Firebase (FCM)) | explicitly NOT planned: adds Google dependency + server config; foreground service + WorkManager remain the honest pair |
| **Web Push** | web app only (browser VAPID) | N/A to Android client |

## 6. Command & reporting ⌘ (PVE gateway) — protocol skeleton (Phase 4+, owner-gated)

- Incoming ntfy message is **data**, never executable (AGENTS §7). Message body parses only if it exactly matches a **versioned JSON envelope**:
```json
{ "kaminari_cmd": { "v": 1, "id": "<uuid>", "action_id": "vm.start|vm.stop|backup.run|…", "target": "<host/vmid>", "issued_at": <unix>, "expires_at": <unix+300>, "nonce": "<random>", "confirm_token": "<sha256 of nonce+action>" } }
```
- Rejection rules: unknown `action_id`; expired; replayed `id`/`nonce` (persisted nonce cache, TTL ≥ 24 h); missing `confirm_token` for destructive actions (`vm.stop`, any delete/backup-delete).
- Reporting becomes **four distinct truthful states** the app can show: `submitted` (publish accepted by server: HTTP 200 = caching confirmed only), `dispatched` (gateway pulled it), `executed` (gateway result payload ack), `notified` (a FCM/human visibility claim). **Never claim delivered-to-handset or read-by-human** — those need the receiver-side ack protocol; unimplemented today, honest UI = show only transport states.
- Destructive (`vm.stop`, backup delete) → requires UI confirmation + independent policy check in gateway; never orchestrate PVE from arbitrary message text.

## 7. Version-dependent behavior the app MUST detect

| Feature | Server version | Prototype usage now |
|---|---|---|
| same-second reconnect dupes on since= (must dedupe by id) | v2.31+ | dedupe exists (in-RAM) — move durable in Phase 1 |
| X-Messages-Truncated / 42905 replay-cap | 2026 docs | not handled |
| topic subscription limit 150 | v2.33+ | no cap |
| tags ≤512B, title ≤1KB caps | v2.28+ | not enforced |

Server version capture = owner-run `ntfy version` (decision D3). No writes, no publish attempts, respects READ-ONLY audit mode.

*(Companion to AUDIT_REPORT.md; Phase-1 test plan mirrors each row.)*
