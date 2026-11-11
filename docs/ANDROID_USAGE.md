# Android usage

## Inbox

The Inbox shows Room-backed messages for the active profile. Use the search field for a bounded `LIKE` search, the saved filter, unread/read filters and topic filters. Tap a message for details, copy text, open a click/attachment URL with confirmation, save or delete it. Samples are clearly labelled and are off by default; they never mix with live rows.

## Topics

Add a topic by name. The app validates ntfy topic syntax (letters, numbers, hyphens, underscores). Each topic shows cached message count and unread count. Unsubscribing asks for confirmation; it removes the subscription, not the existing history.

## Compose

The Compose screen sends a publish through the outbox:

1. Topic (must be subscribed or explicitly entered).
2. Message body.
3. Optional title, priority, tags, click URL, attachment URL and delay.
4. **Queue & Send**.

The screen shows validation errors before any network call. On success it reports **server accepted**, not delivery. On failure it distinguishes `FAILED` from `AMBIGUOUS`; an ambiguous publish shows an explicit duplicate-risk warning before retry. The draft is preserved if sending fails.

## Outbox

Each row shows its state, attempt count, topic and timestamps:

- `QUEUED` — waiting for a send.
- `SENDING` — claimed by one sender.
- `ACCEPTED` — server accepted the request.
- `FAILED` — definitive or retryable failure.
- `AMBIGUOUS` — timeout/unknown; the server may have accepted it.

Retry is manual only. `AMBIGUOUS` retry requires an explicit confirmation because ntfy has no idempotency key. Cancel is available only before sending.

## Activity

A bounded timeline of relevant events: publishes, receives, retries, delivery-state changes and mock commands. Filter by All, Publish, Inbox or Attention. Summaries do not contain message bodies. Retention: 90 days / 5,000 events per profile; queued work is never pruned.

## Command

The Command Centre creates a typed `rplab.command.v1` envelope. The action list is fixed and allowlisted. A destructive action requires a second confirmation tied to its nonce. The result is explicitly labelled **MOCK — no real infrastructure changed**. Nothing is sent to Proxmox.

## Settings

- Active server and profile list.
- Add, edit and select profiles; URL-change preview.
- Delivery mode: Instant or Battery Saver.
- Notification permission status.
- Theme: Standard (Parchment/Observatory) or E-Ink.
- Retention and privacy notices.
- Logout/credential removal.

## E-Ink

Switch to **E-Ink** in Settings. The profile uses a monochrome palette, solid borders, static status markers, larger touch targets and no gradients, shadows, shimmer or continuous animation. It is a complete presentation profile, not a grayscale filter.
