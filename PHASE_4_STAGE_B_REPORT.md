# Phase 4 — Stage B Report: ntfy Publish Client

**Date:** 2026-11-10
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**HEAD before Stage B:** `49e3159` — *Stage A.1: Room v2 persistence foundation hardening*
**Scope:** publish-client foundation only (no outbox orchestration, no UI, no command execution)
**Checkpoint commit:** recorded in `PHASE_4_STAGE_B_REPORT.md` at the follow-up docs commit

---

## 1. Implemented functionality

### 1.1 Authentication (`data/api/NtfyAuth.kt`)

| Method | Wire behavior |
|---|---|
| `GUEST` | No `Authorization` header is sent. |
| `USERNAME_PASSWORD` | `Authorization: Basic base64(username:password)` via OkHttp `Credentials.basic`. |
| `ACCESS_TOKEN` | `Authorization: Bearer <token>`. |

- Credentials are emitted **only** as an `Authorization` header. They are never
  placed in a URL, query string, body or log line.
- CR/LF are stripped from a pasted credential before it reaches the header
  (header-injection guard).
- A method whose secret is missing produces **no** header; publish validation
  rejects the request before any network call, so publish never silently falls
  back to guest.
- `NtfyStreamClient` now delegates to `NtfyAuth` so receive and publish share one
  authentication implementation. Behaviour is unchanged (all stream tests pass).

### 1.2 Validation (`data/api/PublishValidator.kt`)

Pure, network-free validation returning the first human-readable failure:

- **topic** — 1–64 chars of `A-Z a-z 0-9 _ -` (same charset as the receive path).
- **message** — must not be blank.
- **priority** — integer `1..5` (ntfy range; default `3`).
- **tags** — each non-blank, no comma, no CR/LF.
- **click URL / attachment URL** — must be absolute `http://` or `https://` URLs
  with a host. Relative paths and other schemes are rejected.
- **delay** — a duration (`1..6` digits + `s|m|h|d`, e.g. `30m`) or a Unix
  timestamp (9–11 digits). Free text is rejected.
- **server URL** — must be present and is normalised by `NtfyEndpoint` with the
  profile's `useHttpsOnly` policy; URLs containing embedded credentials, a query
  string or a fragment are rejected.
- **credentials** — Basic requires username *and* password; Bearer requires a
  token. Missing credentials are a validation failure, not an unauthenticated
  request.

### 1.3 Publish URL (`data/api/NtfyEndpoint.kt`)

`publishUrl(base, topic, requireHttps)` builds `POST {base}/{topic}` using an
OkHttp `HttpUrl` builder and a single validated path segment. It reuses the
existing normalisation so HTTPS enforcement, credential-in-URL rejection and
query/fragment rejection are identical to the receive path.

### 1.4 HTTP publishing (`data/api/NtfyPublishClient.kt`)

- Endpoint: `POST {base}/{topic}`.
- Body:
  - `text/plain; charset=utf-8` with just the message when no metadata is set.
  - `application/json; charset=utf-8` when any of title, tags, click, attach or
    delay is present. JSON fields: `message`, optional `title`, `priority`,
    optional `tags` (array), optional `click`, optional `attach`, optional
    `delay`. JSON is encoded locally with proper escaping (no `org.json`
    dependency), which keeps the unit tests hermetic on the JVM.
- A successful response body that contains `"id":"…"` yields that server message
  id; otherwise the id is `null`.
- Bounded timeouts: connect 15 s, read 15 s, write 20 s, whole-call 30 s.
- **No retries and no automatic resend.** An in-flight request has exactly one
  attempt.

### 1.5 Result handling (`PublishResult`)

| Result | When |
|---|---|
| `Accepted(serverMessageId)` | HTTP 2xx. **Means the server accepted the publish; it does not mean a recipient received it.** |
| `ValidationFailed(reason)` | Local validation, invalid URL, HTTP 400, 404, 413. |
| `AuthenticationFailed(statusCode, reason)` | HTTP 401. |
| `PermissionFailed(statusCode, reason)` | HTTP 403 (distinct from authentication). |
| `ServerError(statusCode, reason)` | HTTP 429, 5xx, and any other unexpected status. |
| `NetworkError(reason, cause)` | Definite transport failure (`IOException`): DNS, TLS, timeout, refused. |
| `Ambiguous(reason)` | A non-transport failure after the request was built/sent. Callers must **not** auto-resend it; Stage C will require manual retry. |

Response bodies are read defensively (`body?.string().orEmpty()`) with a
fallback reason when the body is empty.

### 1.6 Server/profile correctness (`NftyRepository`)

- `publish(profileId, request)` resolves the `ServerConfig` only through
  `ProfileRepository.loadConfig(profileId)`, which reads the URL/method from
  Room and the secret from the existing Android Keystore-backed `SecretStore`.
- Blank profile id → `ValidationFailed` (no publisher call).
- Unknown profile → `ValidationFailed` (no publisher call, no fallback).
- There is deliberately **no** path that substitutes another profile's config.
  A test asserts two profiles with different tokens never cross.
- `NtfyRepository` gained injectable trailing constructor parameters
  (`profileRepository`, `publisher`) with defaults, so existing streaming tests
  and call sites are untouched. No delivery behavior changed.
- **No database or entity change was made.** An earlier draft that copied
  credentials into `ServerProfileEntity` was discarded: secrets stay in
  `SecretStore`, matching Phase 1–3 architecture and the "never expose
  credentials" requirement.

---

## 2. Files changed

### Added

| File | Purpose |
|---|---|
| `app/src/main/java/com/rplab/kaminarinfty/data/api/NtfyAuth.kt` | Shared auth header construction + header sanitisation |
| `app/src/main/java/com/rplab/kaminarinfty/data/api/PublishValidator.kt` | Pure publish validation |
| `app/src/main/java/com/rplab/kaminarinfty/data/api/NtfyPublishClient.kt` | `PublishRequest`, `PublishResult`, `NtfyPublish`, HTTP client |
| `app/src/test/java/com/rplab/kaminarinfty/data/api/NtfyAuthTest.kt` | 6 auth tests |
| `app/src/test/java/com/rplab/kaminarinfty/data/api/PublishValidatorTest.kt` | 11 validation tests |
| `app/src/test/java/com/rplab/kaminarinfty/data/api/NtfyPublishClientTest.kt` | 17 MockWebServer contract tests |
| `app/src/test/java/com/rplab/kaminarinfty/data/repository/NftyRepositoryPublishTest.kt` | 5 profile-scope tests |
| `PHASE_4_STAGE_B_REPORT.md` | This report |

### Modified

| File | Change |
|---|---|
| `app/src/main/java/com/rplab/kaminarinfty/data/api/NtfyEndpoint.kt` | Added `publishUrl(...)` |
| `app/src/main/java/com/rplab/kaminarinfty/data/api/NftyStreamClient.kt` | `applyAuthHeaders` delegates to `NtfyAuth`; removed duplicated private helper |
| `app/src/main/java/com/rplab/kaminarinfty/data/repository/NftyRepository.kt` | Added injectable `profileRepository`/`publisher`; added `suspend fun publish(profileId, request)` |

No changes to database schema, entities, migrations, delivery modes, services,
workers, Compose UI or navigation.

---

## 3. Tests added (39 new tests)

`NtfyPublishClientTest` (MockWebServer, no real server):

- guest publish sends no `Authorization`, posts to `/{topic}`, parses `{"id":…}`;
- Basic header equals `Credentials.basic(...)`;
- Bearer header equals `Bearer <token>`;
- invalid topic / blank message / priority 9 / bad click URL / bad delay are
  rejected with **zero** requests sent;
- missing access token rejected with zero requests;
- credentials embedded in the server URL rejected with zero requests;
- JSON body contains message, title, priority, tags array, click, attach and
  delay with the JSON content type;
- message-only publish uses a plain-text body and a null message id;
- 401 → `AuthenticationFailed`, 403 → `PermissionFailed`,
  400 → `ValidationFailed`, 500 → `ServerError`;
- unreachable port → `NetworkError`.

`NtfyAuthTest`: guest no header, Basic encoding, Bearer, CRLF stripping, missing
password/token yields no header.

`PublishValidatorTest`: valid payload passes; invalid topic, blank message,
priority 0/6, comma tag, non-HTTP click/attach URL, bad delay, missing Basic
credentials, missing Bearer token, blank server URL are rejected; Unix-timestamp
delay accepted.

`NftyRepositoryPublishTest` (fake profile repository recording publisher):

- publishes with the selected profile's config;
- two profiles with different tokens never cross;
- unknown profile rejected without publishing;
- blank profile id rejected without publishing;
- missing profile storage rejected.

---

## 4. Verification (run on the final code, 2026-11-10)

```text
./gradlew testDebugUnitTest
# exit 0 — 240 tests, 0 failures, 0 errors, 2 pre-existing skips
#   (39 new Stage B tests; NtfyAuth 6, PublishValidator 11,
#    NtfyPublishClient 17, NftyRepositoryPublish 5)

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,720,707 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings (unchanged from the Phase 3 / Stage A baseline)
```

Environment notes (not app defects):

- `repo.maven.apache.org` did not resolve on this host, while
  `repo1.maven.org` did. Verification used a **temporary, uncommitted** Gradle
  init script in `/tmp` that mirrors Maven Central to `repo1.maven.org`, and the
  configuration cache was cleared. No build files or repositories were changed.
- The git-ignored `debug.keystore` had been removed during cleanup and was
  regenerated with the same conventional debug alias/credentials so
  `assembleDebug` could run. It is ignored and not part of the commit.
- No device or emulator is available (`adb` not installed), so no on-device
  publish or Keystore round-trip was performed.

---

## 5. Limitations and carried risks

| Item | State |
|---|---|
| Real ntfy server publish | **Not verified.** `https://nfty.rplab.qzz.io/v1/health` previously returned HTTP 502; all Stage B evidence is MockWebServer. R-17 remains open. |
| Device validation | Not possible here; R-23/device matrix remains owner/hardware-gated. |
| Binary attachments | Out of scope by design: only URL/metadata (`attach`) is sent; no file upload. |
| Retries | Intentionally none; ambiguous outcomes are surfaced, never auto-resent. Stage C owns any retry policy. |
| `Ambiguous` trigger | Current triggers are non-transport exceptions. A socket timeout after the server accepted the message is classified as `NetworkError`; Stage C must treat any uncertain delivery as requiring manual retry rather than resend. |
| JSON encoding | Hand-rolled escaping for hermetic JVM tests; covered by body assertions but not exhaustively fuzzed. |
| Secrets in memory | `ServerConfig` carries the secret for the duration of a publish, as the receive path already does; no logging was added. |

---

## 6. Git checkpoint

- Stage A.1 checkpoint (start of Stage B): `49e3159`.
- Stage B code + docs checkpoint: reported after commit in the follow-up
  documentation commit (this file is updated with the exact hash).

---

## 7. Scope boundaries honoured

- No outbox orchestration, queueing or automatic retry (Stage C).
- No Compose publish UI or navigation (Stage D).
- No command envelope, execution or PVE integration (Stage E / Phase 6).
- No binary attachment uploads.
- No change to completed Phase 0–3 behaviour; the stream client's auth refactor
  is behaviour-preserving and covered by the existing tests.

---

## 8. Next

1. Owner review of this report and the Stage B diff.
2. Explicit owner authorisation before Stage C (outbox/activity/command
   repositories and the receive-event transaction).
3. When the ntfy origin is healthy, run a real publish smoke test against a
   dedicated test topic and record the result in `PHASE_4_REPORT.md`.
