# ntfy integration

## Documented endpoints used by the app

| Operation | Method | Path | Notes |
|---|---|---|---|
| Health | GET | `/v1/health` | public; `200` + JSON `healthy` |
| Server config | GET | `/v1/config` | used by diagnostics; not required for receiving |
| Stream | GET | `/{topics}/json?since=...` | one JSON object per line; supports multiple comma-separated topics |
| Poll/catch-up | GET | `/{topic}/json?poll=1&since=...` | used by Battery Saver catch-up |
| Publish | POST | `/{topic}` | documented ntfy publish; JSON body for metadata |
| Admin users/tokens | — | `/v1/users` etc. | undocumented/internal; not part of v1; never required for core app |

Official references: <https://docs.ntfy.sh/subscribe/api/>, <https://docs.ntfy.sh/publish/>, <https://docs.ntfy.sh/config/>, <https://docs.ntfy.sh/releases/>.

## Authentication

`NtfyAuth` supports:

- **Guest:** no header.
- **Basic:** `Authorization: Basic base64(username:password)`.
- **Bearer:** `Authorization: Bearer tk_...`.

CR/LF is stripped from credentials before header construction. Credentials are held only in memory for the request; the encrypted envelope is in `SecretStore`.

The live owner server has auth disabled, so Guest is the correct mode. If auth is enabled later, create a non-admin `kaminari_app` token scoped to the approved topics; never use an admin token in the app.

## Stream behavior

- Topics are sent as one comma-separated path segment.
- The stream uses a long read timeout and reconnect/backoff.
- `open`, `keepalive`, `message`, `poll_request` and unknown event types are handled without crashing.
- Malformed lines are counted and surfaced as a sanitized diagnostic; they are never silently swallowed.
- The `since` cursor is persisted so a reconnect can catch up. The current implementation uses a 12-hour initial window unless a cursor exists.
- A successful stream connection proves transport connected; it does not prove topic read permission or admin role. The UI separates `REACHABLE — AUTH UNVERIFIED`, `GUEST SESSION`, `AUTHENTICATED`, `PERMISSION DENIED` and `OFFLINE`.

## Publish behavior

`NtfyPublishClient` supports title, priority, tags, click, attachment URL and delay. The JSON body is used when metadata is present; plain text is used for a simple body. It follows no redirects and does not retry automatically.

Result semantics:

- `2xx` → `ACCEPTED` (server receipt).
- `401`/`403` → `FAILED` (non-retryable until credentials change).
- `429`/`5xx`/connect failure → `FAILED(retryable=true)`.
- timeout/unknown → `AMBIGUOUS`; manual retry requires duplicate-risk confirmation.

ntfy has no idempotency key. A retry after an ambiguous request can duplicate the message. This is a protocol limitation, not a hidden guarantee.

## Local history and deduplication

Room deduplicates on `(server_profile_id, ntfy_message_id)`. A duplicate stream event is ignored; no second activity event is written. Read/saved state is local and is not an ntfy read receipt.

## Catch-up and sync

Battery Saver uses a bounded polling query with `poll=1` and the persisted `since` cursor. It does not use an unbounded backfill. If the cursor is older than the server cache, the app receives whatever the server retains and reports a gap rather than claiming complete history.
