#!/usr/bin/env python3
"""Read-only diagnosis of the music pipeline's failed jobs.

Opens `pipeline.db` with `mode=ro`, so nothing can be retried, requeued or
modified. It answers: how many failed, since when, with which error, and whether
the failure is uniform (one systemic cause) or scattered.

Usage (on the host that runs the pipeline):
  python3 ops/verify/music_pipeline_diagnose.py [--db PATH] [--limit 10]
"""

from __future__ import annotations

import argparse
import sqlite3
from collections import Counter
from pathlib import Path

DEFAULT_DB = Path("/var/lib/docker/volumes/deploy_music_pipeline_data/_data/pipeline.db")


def columns(db: sqlite3.Connection, table: str) -> list[str]:
    return [row[1] for row in db.execute(f"PRAGMA table_info({table})")]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--limit", type=int, default=8)
    args = parser.parse_args()

    if not args.db.exists():
        print(f"db not found: {args.db}")
        return 1

    db = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    try:
        tables = [
            row[0]
            for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
        ]
        print("tables:", ", ".join(tables))

        for table in tables:
            cols = columns(db, table)
            if "status" not in cols:
                continue
            counts = Counter()
            for (status,) in db.execute(f"SELECT status FROM {table}"):
                counts[str(status)] += 1
            print(f"\n[{table}] total={sum(counts.values())} by status: {dict(counts)}")

            if "status" not in cols:
                continue
            failed = counts.get("failed", 0) + counts.get("error", 0)
            if not failed:
                continue

            statuses = [s for s in ("failed", "error") if counts.get(s)]
            placeholders = ",".join("?" for _ in statuses)
            order = "updated_at" if "updated_at" in cols else ("created_at" if "created_at" in cols else "rowid")

            print(f"  failed rows: {failed}")
            if "created_at" in cols:
                first, last = db.execute(
                    f"SELECT MIN(created_at), MAX(created_at) FROM {table} "
                    f"WHERE status IN ({placeholders})",
                    statuses,
                ).fetchone()
                print(f"  first/last created: {first} .. {last}")

            error_col = next((c for c in ("error", "error_message", "last_error", "message") if c in cols), None)
            if error_col:
                rows = db.execute(
                    f"SELECT {error_col}, COUNT(*) FROM {table} "
                    f"WHERE status IN ({placeholders}) GROUP BY {error_col} ORDER BY 2 DESC LIMIT {args.limit}",
                    statuses,
                ).fetchall()
                print("  top errors:")
                for message, count in rows:
                    text = " ".join(str(message or "").split())[:220]
                    print(f"    {count:>4}x  {text}")
            else:
                print(f"  no error column (columns: {', '.join(cols)})")

            retry_col = next((c for c in ("retry_count", "attempts") if c in cols), None)
            if retry_col:
                attempts = db.execute(
                    f"SELECT {retry_col}, COUNT(*) FROM {table} WHERE status IN ({placeholders}) "
                    f"GROUP BY {retry_col} ORDER BY {retry_col}",
                    statuses,
                ).fetchall()
                print(f"  retry_count distribution: {dict(attempts)}")
                retryable = db.execute(
                    f"SELECT COUNT(*) FROM {table} WHERE status IN ({placeholders}) "
                    f"AND {retry_col} < COALESCE(max_retries, 0)",
                    statuses,
                ).fetchone()[0]
                print(f"  failed rows with retries left: {retryable}")

            if "needs_approval" in cols:
                approval = db.execute(
                    f"SELECT needs_approval, COUNT(*) FROM {table} WHERE status IN ({placeholders}) "
                    "GROUP BY needs_approval",
                    statuses,
                ).fetchall()
                print(f"  needs_approval: {dict(approval)}")

            sample_cols = [c for c in ("id", "raw_input", "resolved_artist", "resolved_title", "status_detail",
                                       "source_agent", "created_at", "completed_at") if c in cols]
            if sample_cols:
                print("  samples (newest first):")
                rows = db.execute(
                    f"SELECT {', '.join(sample_cols)} FROM {table} WHERE status IN ({placeholders}) "
                    f"ORDER BY {order} DESC LIMIT 5",
                    statuses,
                ).fetchall()
                for row in rows:
                    cells = [f"{c}={str(v)[:60]}" for c, v in zip(sample_cols, row)]
                    print("    " + " | ".join(cells))
    finally:
        db.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
