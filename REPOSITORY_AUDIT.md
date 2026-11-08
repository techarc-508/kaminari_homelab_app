# Kaminari Nfty — Supplied ZIP Repository Audit

**Audit scope:** Static inspection of `kaminari_homelab_app-main.zip`, extracted on 2026-11-08. This is not a build, device, penetration or live-server test.

## Executive verdict

The repository is a promising native Compose UI prototype with a partial ntfy JSON-stream client. It is not yet a dependable daily-use notification client. The largest gaps are not visual: production data persistence, Android notification delivery, background execution, publishing, command acknowledgement, secure secret persistence and real server administration are missing or incomplete.

**Build status: NOT VERIFIED.** The ZIP contains `gradle/wrapper/gradle-wrapper.properties`, but no root `gradlew` script and no `gradle/wrapper/gradle-wrapper.jar`. The build/test task could not be run from the supplied snapshot.

## Evidence-backed findings

### Critical — production behavior

1. **Sample data is the initial state.** `MainViewModel.kt` initializes `messages` and `topics` from `SampleDataProvider`; `init` registers sample IDs and calls `restartLiveStream()`. Production and demonstration states are therefore coupled.
2. **Default connection starts automatically.** `ServerConfig` defaults to `https://nfty.rplab.qzz.io` and guest auth; `MainViewModel.init` starts a stream if sample topics are marked subscribed. This is risky if the host or topic defaults are not intentionally confirmed.
3. **No persistent message store was found.** Message lists, read/saved flags, topic subscriptions and banner state are in `MainUiState`. Room and DataStore libraries are declared, but no Room database/entities/DAOs or DataStore implementation exist in the supplied source tree.
4. **No Android notification posting implementation was found.** Manifest requests INTERNET and ACCESS_NETWORK_STATE only. There are no notification channels, notification permission request, `NotificationManager` delivery implementation, service or receiver in the supplied tree.
5. **No publish method was found in `NftyStreamClient`.** It implements health-ish connection probing and JSON stream reading, not message publishing.
6. **No command protocol or acknowledgement/result store was found.** No command envelope, command ID correlation, allowlist, executor adapter or end-to-end report state exists.
7. **No real admin layer was found.** Topic objects and permissions are locally constructed UI models. Local subscribe/pause toggles are not server-side ACL/user/token administration.
8. **No background delivery architecture was found.** No foreground service, WorkManager worker, FCM receiver or UnifiedPush integration exists.

### High — correctness and reliability

9. **Connection state overstates authentication.** In `NftyStreamClient.testConnection`, HTTP 200/204 on `/v1/health` maps to `AUTHENTICATED` for username/password or access-token mode if the token/password field is merely nonblank. Health success does not prove that credentials were accepted or that the user is an administrator.
10. **Reconnect handling needs correction.** `NftyRepository` receives non-retryable auth/forbidden events and updates state, but the outer reconnect loop still reaches its backoff/retry path after the stream collection ends. Permanent 401/403 errors may therefore be retried indefinitely.
11. **Deduplication is unbounded and non-durable.** `processedMessageIds` is an in-memory mutable set without retention/size cap. It is lost on process death and grows during long-running sessions.
12. **Stream networking uses blocking OkHttp `execute()` inside a coroutine Flow.** There is no explicit cancellable-call bridge. The long-lived stream uses `readTimeout(0)`; response close/cancellation behavior must be tested under topic changes, app stop and network transitions.
13. **Malformed stream events are silently discarded.** `parseJsonLine` catches broad exceptions and returns null without a counter or diagnostic. This makes protocol drift hard to detect.
14. **No durable catch-up cursor is persisted.** The stream starts with a fixed `since = "12h"` default; there is no last-seen ID/time checkpoint integrated with a database transaction.
15. **Local read/saved/delete semantics are not durable.** `markAsRead`, `toggleSaveMessage`, `deleteMessage`, `clearHistory` and `clearSaved` mutate in-memory lists only.
16. **Topic management is local only.** `addTopic` sanitizes a name and creates a local topic; no remote ACL/permission discovery is shown. `TopicPermission` values should not be presented as verified server permissions.
17. **`MainViewModel` is a large state/behavior coordinator.** It handles navigation, data state, sample mode, connection, topic management, notifications, settings and message actions. Split responsibilities by feature/repository.
18. **Credential state is plain in-memory model data.** `ServerConfig.tokenOrPassword` is held in ordinary app state. No encrypted persistence is present. Avoid logging or saving it without Keystore-backed encryption.

### Medium — product/build quality

19. **No dedicated E-Ink profile exists.** Current theme setting is SYSTEM/NIGHT/DAY; there is no monochrome mode, refresh policy or animation suppression profile.
20. **Low-memory behavior is not demonstrated.** No paging, database-backed history, performance benchmarks or bounded cache policy exists.
21. **Build includes unrelated/unused integration surface.** Firebase AI/App Check/Google Services plugins/dependencies appear in the module, while CameraX/location/Coil declarations are commented or unused. Perform reference-level audit before removal.
22. **Package identity is provisional.** Namespace is `com.example`; application ID is `com.aistudio.kaminarinfty.rzxqpt`. Confirm the permanent application ID before producing user installs/signing keys.
23. **Tests are example-level.** Current tests cover sample loading, local save/read behavior, default server config and priority mapping. No API contract, persistence, notification, lifecycle, migration, admin or command tests exist.
24. **The current test suite asserts demo data exists.** This test must change when production startup becomes empty and demo mode becomes explicit.
25. **No complete release pipeline was supplied.** Release signing references environment variables and a keystore path; no keystore should be committed. The missing wrapper also blocks reproducible builds.

## Feature truth table

| Capability | Current status |
|---|---|
| Compose inbox/topics/settings/detail/search/saved screens | UI exists; not evidence of full end-to-end behavior |
| JSON stream subscription | Partial implementation exists |
| Basic/Bearer header construction | Exists; auth verification/status semantics need correction |
| Health endpoint probe | Exists; success is over-interpreted as authenticated/admin |
| Stream reconnect | Partial; permanent auth failures need fix and tests |
| Durable notifications/history | Missing |
| Android system notifications | Missing |
| Background instant delivery | Missing |
| Battery-saver catch-up | Missing |
| Publish messages | Missing |
| Send commands / correlate results | Missing |
| Server-side user/ACL/token admin | Missing |
| Server config/backup/restart admin | Not a normal ntfy client capability; requires separate trusted server interface |
| E-Ink profile | Missing |
| Explicit demo mode | Not properly isolated; sample data is default |
| Build/test verification | Blocked by absent Gradle wrapper files in ZIP |

## Architecture corrections in priority order

1. Restore a reproducible build baseline and capture current test results.
2. Separate demo data from production startup; never auto-subscribe to guessed topics.
3. Introduce Room as source of truth for messages, topic settings, outbox, delivery events and command history.
4. Persist non-secret preferences with DataStore and protect credentials with Android Keystore-backed encryption.
5. Separate transport connectivity, authentication, per-topic authorization and admin role.
6. Rebuild stream lifecycle around cancellation-safe calls, durable catch-up, bounded retry/dedupe and structured diagnostics.
7. Add a notification pipeline and explicit Android background-delivery modes.
8. Add publishing and honest delivery states.
9. Add a safe structured command protocol and trusted server-side executor integration; never execute arbitrary shell from message text.
10. Implement E-Ink and low-resource profiles after data/network foundations are reliable.
11. Add verified admin capabilities only where the server API supports them; keep undocumented admin endpoints isolated and version-gated.

## Important ntfy integration constraints

- ntfy supports a JSON HTTP stream (`/{topic}/json`), polling/catch-up via `poll` and `since`, and HTTP publishing. See https://docs.ntfy.sh/subscribe/api/ and https://docs.ntfy.sh/publish/.
- ntfy health endpoint `/v1/health` reports server health; it does not by itself prove the supplied app credentials have admin privileges. See https://docs.ntfy.sh/config/.
- ntfy access control uses user roles and per-topic ACLs. The documented management path includes the server CLI/config; some admin HTTP endpoints are explicitly undocumented/internal. Do not treat them as stable public contracts. See https://docs.ntfy.sh/config/ and https://docs.ntfy.sh/releases/.
- Self-hosted Android instant delivery generally requires a persistent live connection/foreground execution unless the server and app are configured for a supported push path. FCM for a self-hosted server requires server-side Firebase configuration and a corresponding custom app build; it is not obtained by adding Firebase libraries alone. See https://docs.ntfy.sh/config/ and https://docs.ntfy.sh/faq/.
- A successful publish HTTP response proves server acceptance, not that another device displayed or a human read the message. End-to-end acknowledgement requires a receiver/executor to publish a correlated acknowledgement.

## Unverified assumptions / owner decisions

- Confirm `https://nfty.rplab.qzz.io` is the correct live ntfy hostname and TLS certificate.
- Confirm actual ntfy server version and whether auth, FCM, UnifiedPush, attachments, metrics or admin capabilities are enabled.
- Confirm exact Onyx Boox model/firmware and one representative low-end Android device.
- Confirm permanent Android application ID and signing strategy.
- Decide whether PVE/homelab command execution is in V1 and, if so, approve a separate trusted executor/gateway design.

## Scope boundary

This audit is based on static source inspection. It does not claim that the current server is reachable, that credentials work, that an APK builds, that the app has been installed on hardware, or that any notification/command was delivered. Those require the gated verification phases in `PLAN.md`.


## Owner decisions received after initial audit

The owner has locked the following requirements for the implementation plan: (1) both Instant and Battery Saver delivery modes; (2) both ntfy administration and PVE/homelab administration, with privileged operations delegated to a separate trusted gateway; (3) two complete UI profiles, Standard Kaminari and dedicated E-Ink; (4) Onyx Boox model remains unknown because the device has not yet arrived. These are product requirements, not claims that the current repository already implements them. Keep model-specific E-Ink tuning deferred until the device is identified.
