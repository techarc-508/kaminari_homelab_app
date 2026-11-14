# Stickers Pipeline Notification Contract

**Topics:** `kf-stickers-pipeline`, `kf-stickers-reports`
**Publisher:** `kf_stickers` (future)
**Status:** **Reserved contract. The pipeline does not exist; no fake publisher is created in Stage 1.**

## 1. Planned stages

| Stage | Event | Topic | Priority |
|---|---|---|---:|
| 1 | Request received | `kf-stickers-pipeline` | 2 |
| 2 | Input validation | pipeline | 2 |
| 3 | Generation/retrieval | pipeline | 2 |
| 4 | Asset review | pipeline | 2 |
| 5 | Background removal | pipeline | 2 |
| 6 | Transparent PNG validation | pipeline | 2 |
| 7 | Resize/format | pipeline | 2 |
| 8 | Pack assembly | pipeline | 2 |
| 9 | Duplicate check | pipeline | 2 |
| 10 | Diary synchronization | pipeline | 3 |
| 11 | Final report | `kf-stickers-reports` | 3 |

## 2. Envelope

```json
{
  "schema": "rplab.stickers.v1",
  "run_id": "2026-11-12T10-30-00Z-<short>",
  "request_id": "diary-request-id",
  "stage": "png_validation",
  "status": "started|success|failed|skipped",
  "timestamp": "2026-11-12T10:30:00Z",
  "asset": {
    "name": "sticker-name",
    "width": 512,
    "height": 512,
    "transparent": true,
    "bytes": 12345
  },
  "pack": "pack-id",
  "error_code": null,
  "error_message": null
}
```

## 3. Final report

```json
{
  "schema": "rplab.stickers.report.v1",
  "run_id": "2026-11-12T10-30-00Z-<short>",
  "requested": 8,
  "generated": 7,
  "rejected": 1,
  "duplicates": 1,
  "packed": 6,
  "synced": 6,
  "failures": [
    {"name": "asset", "stage": "transparent_png", "error_code": "ALPHA_MISSING"}
  ]
}
```

## 4. Message design

- Title: `Stickers — <stage> — <pack>`.
- Tags: `art`, plus `warning` on rejection.
- Priority: 2 for stages, 3 for report, 4 only for a total pipeline failure.
- Click: the future Diary/sticker review page.
- Dedupe key: `run_id + stage + asset`.
- Do not send every generated variant; send pack-level events.

## 5. Safety rules

- Do not claim a sticker is “synced to Diary” until the Diary API confirms.
- Do not include image bytes in ntfy.
- Do not publish to `kf-diary` from this contract; the Diary app owns that topic.
- A transparent PNG check failure is a rejection, not a retry loop unbounded.

## 6. Retention

- Pipeline: 30 days.
- Reports: 90 days.
