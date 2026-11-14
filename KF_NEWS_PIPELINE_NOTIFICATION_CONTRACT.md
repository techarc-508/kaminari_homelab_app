# News Pipeline Notification Contract

**Topics:** `kf-news`, `kf-news-reports`
**Publisher:** `kf_news`
**Status:** Contract for Freebuff. The article database stays outside ntfy.

## 1. Architecture

```
RSS / sources -> ingestion -> dedupe -> relevance -> article store (SQLite/Postgres)
                                      -> card payload -> ntfy kf-news
                                      -> run stats -> kf-news-reports
```

ntfy carries a **card**, not the full article. The Android app has a real News screen backed by a proper API/database; ntfy history is not the article archive.

## 2. Card payload

```json
{
  "schema": "rplab.news.v1",
  "article_id": "stable-id",
  "headline": "Headline",
  "source": "Source name",
  "category": "security|homelab|ai|local",
  "summary": "Two or three sentences.",
  "published_at": "2026-11-12T10:30:00Z",
  "image_url": "https://...",
  "article_url": "https://...",
  "language": "en",
  "relevance": 0.87
}
```

## 3. Message design

- Title: the headline, truncated to 120 characters.
- Message: source · category · published time · summary.
- Tags: `newspaper` plus category tag.
- Priority: 2 normally; 3 for a high-relevance item; 4 only for a breaking item explicitly classified by the source.
- Click: the **original article URL**.
- Image: use the article image URL if available; never download and attach binary in Phase 1.
- Dedupe: canonical URL hash + title similarity; do not send the same article twice.

## 4. Reports

```json
{
  "schema": "rplab.news.report.v1",
  "run_id": "2026-11-12T10-30-00Z",
  "sources": 12,
  "fetched": 340,
  "new": 27,
  "duplicates": 301,
  "rejected": 12,
  "published": 27,
  "failures": [
    {"source": "example", "error_code": "HTTP_404"}
  ]
}
```

Send one report per run to `kf-news-reports`; do not send a report for every source.

## 5. Android News screen

Required:

- Card list with headline, source, category, summary, timestamp and image.
- Filter by category/source.
- Saved articles.
- Pagination backed by the news API, not ntfy.
- Open original URL.
- Empty/offline/error states.
- No full article text stored in ntfy.

## 6. Retention

- `kf-news`: 30 days of cards.
- `kf-news-reports`: 90 days.
- Article database retention is owned by the news service, not ntfy.

## 7. Failure rules

- A failed source is reported; it must not stop other sources.
- A duplicate is counted, not sent.
- Never claim an article was read or saved because a card was delivered.
- Do not scrape paywalled/private content without authorization.
