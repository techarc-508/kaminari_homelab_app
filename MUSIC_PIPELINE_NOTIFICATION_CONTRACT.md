# Music Pipeline Notification Contract

**Topics:** `kf-music-pipeline`, `kf-music-reports`
**Publisher:** `kf_music`
**Status:** Contract for Freebuff; existing Memos → music download → music drive → Navidrome workflow is not modified in Stage 1.

## 1. Stage sequence

| Stage | Event | Topic | Priority |
|---|---|---|---:|
| 1 | Memos detection | `kf-music-pipeline` | 2 |
| 2 | Request validation | pipeline | 2 |
| 3 | Duplicate detection | pipeline | 2 |
| 4 | Source resolution | pipeline | 2 |
| 5 | Download start | pipeline | 2 |
| 6 | Download milestone (25/50/75 %) | pipeline | 1–2 |
| 7 | Download success/failure | pipeline | 3/4 |
| 8 | File validation | pipeline | 2 |
| 9 | Metadata/artwork | pipeline | 2 |
| 10 | Library placement | pipeline | 2 |
| 11 | Navidrome scan | pipeline | 2 |
| 12 | Final report | `kf-music-reports` | 3 |

Do not send a notification for every file-system operation. Send stage transitions, failures and milestones.

## 2. Envelope

```json
{
  "schema": "rplab.music.v1",
  "run_id": "2026-11-12T10-30-00Z-<short>",
  "request_id": "memos-note-id",
  "stage": "download_start",
  "status": "started|success|failed|skipped",
  "timestamp": "2026-11-12T10:30:00Z",
  "track": {
    "artist": "Artist",
    "title": "Title",
    "album": "Album"
  },
  "source": "source-name",
  "attempt": 1,
  "progress": 25,
  "error_code": null,
  "error_message": null
}
```

## 3. Final report

```json
{
  "schema": "rplab.music.report.v1",
  "run_id": "2026-11-12T10-30-00Z-<short>",
  "started_at": "2026-11-12T10:30:00Z",
  "finished_at": "2026-11-12T10:34:12Z",
  "requested": 12,
  "downloaded": 9,
  "skipped": 2,
  "failed": 1,
  "indexed": 9,
  "failures": [
    {"title": "Track", "stage": "download", "error_code": "SOURCE_404"}
  ]
}
```

## 4. Message design

- Title: `Music — <stage> — <artist> - <title>` (truncate to 100 chars).
- Message: one or two useful lines; no full logs.
- Tags: `musical_note`, plus `warning`/`x` on failure.
- Priority: 3 for success, 4 for failure, 1–2 for progress.
- Click: the music application or Navidrome URL, not a destructive action.
- Dedupe key: `run_id + stage + track identity`; suppress duplicate milestones.
- Cooldown: progress milestones at most one per 20 % or 30 seconds.

## 5. Failure rules

- A single failed track must not abort the whole batch unless the source is unavailable.
- Final report must count requested, downloaded, skipped, failed and indexed.
- A failed Navidrome scan is a warning, not a claim that the file is unplayable.
- Never say “added to library” before the library placement and scan stage succeed.

## 6. Retention

- `kf-music-pipeline`: 30 days.
- `kf-music-reports`: 90 days.
- Keep the full run log outside ntfy.
