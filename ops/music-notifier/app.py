#!/usr/bin/env python3
"""Kaminari music pipeline -> ntfy notifier.

The music pipeline (music-pipeline-api) is the only producer here: this
service never invents events, it forwards what the pipeline reports.

* Every new pipeline log event that carries a job id is published to
  `kf-music-pipeline`.
* A real state digest (job counts by status + the most recent job) is published
  to `kf-music-reports` at startup and then whenever the job status set changes.

Configuration (environment):
  PIPELINE_URL    http://music-pipeline-api:8600
  NTFY_URL        http://192.168.0.104:8201
  NTFY_TOKEN      kf_music token (write-only on the two kf-music-* topics)
  POLL_SECONDS    30
  STATE_PATH      /data/state.json
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

PIPELINE_URL = os.environ.get("PIPELINE_URL", "http://music-pipeline-api:8600").rstrip("/")
NTFY_URL = os.environ.get("NTFY_URL", "http://192.168.0.104:8201").rstrip("/")
NTFY_TOKEN = os.environ.get("NTFY_TOKEN", "")
TOPIC_EVENTS = os.environ.get("TOPIC_EVENTS", "kf-music-pipeline")
TOPIC_REPORTS = os.environ.get("TOPIC_REPORTS", "kf-music-reports")
POLL_SECONDS = int(os.environ.get("POLL_SECONDS", "30"))
STATE_PATH = os.environ.get("STATE_PATH", "/data/state.json")
MAX_EVENT_CHARS = 400


def log(message: str) -> None:
    stamp = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"{stamp} {message}", flush=True)


def get_json(path: str, timeout: int = 10):
    with urllib.request.urlopen(PIPELINE_URL + path, timeout=timeout) as response:
        return json.loads(response.read().decode())


def fetch_jobs() -> list[dict]:
    """`/api/jobs` returns a bare list; tolerate a wrapped form too."""
    data = get_json("/api/jobs?limit=50")
    if isinstance(data, dict):
        data = data.get("jobs", [])
    return data or []


def publish(topic: str, title: str, message: str, tags: list[str], priority: int = 3) -> bool:
    payload = json.dumps(
        {
            "topic": topic,
            "title": title,
            "message": message[:MAX_EVENT_CHARS],
            "priority": priority,
            "tags": tags,
            "click": f"{PIPELINE_URL}",
        }
    ).encode()
    request = urllib.request.Request(NTFY_URL, data=payload, method="POST")
    request.add_header("Content-Type", "application/json")
    if NTFY_TOKEN:
        request.add_header("Authorization", "Bearer " + NTFY_TOKEN)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status == 200
    except urllib.error.HTTPError as error:
        log(f"publish failed topic={topic} http={error.code} {error.read()[:120]!r}")
        return False
    except Exception as error:  # noqa: BLE001
        log(f"publish failed topic={topic} {type(error).__name__}: {error}")
        return False


def load_state() -> dict:
    try:
        with open(STATE_PATH, encoding="utf-8") as handle:
            return json.load(handle)
    except Exception:  # noqa: BLE001
        return {}


def save_state(state: dict) -> None:
    try:
        os.makedirs(os.path.dirname(STATE_PATH), exist_ok=True)
        tmp = STATE_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(state, handle)
        os.replace(tmp, STATE_PATH)
    except Exception as error:  # noqa: BLE001
        log(f"state save failed: {error}")


def digest(jobs: list[dict]) -> tuple[str, str]:
    counts: dict[str, int] = {}
    for job in jobs:
        counts[job.get("status") or "unknown"] = counts.get(job.get("status") or "unknown", 0) + 1
    parts = ", ".join(f"{name}={count}" for name, count in sorted(counts.items()))
    latest = jobs[0] if jobs else None
    if latest:
        message = (
            f"{len(jobs)} jobs: {parts}.\n"
            f"Latest: {latest.get('raw_input')} ({latest.get('status')})"
            f"{' - ' + str(latest.get('detail')) if latest.get('detail') else ''}"
        )
    else:
        message = "Pipeline has no jobs yet."
    return "Music pipeline state", message


def status_snapshot(jobs: list[dict]) -> dict:
    return {str(job.get("id")): job.get("status") for job in jobs}


def main() -> int:
    if not NTFY_TOKEN:
        log("NTFY_TOKEN is not set; refusing to run without credentials")
        return 2

    state = load_state()
    last_event_id = int(state.get("last_event_id", 0))
    previous_jobs = state.get("jobs") or {}

    log(f"starting: pipeline={PIPELINE_URL} ntfy={NTFY_URL} topics={TOPIC_EVENTS},{TOPIC_REPORTS}")

    # First run: establish a baseline without replaying history, then publish a
    # real digest so the channel proves itself with production data.
    try:
        jobs = fetch_jobs()
        events = get_json("/api/logs?limit=1").get("events", [])
        if events:
            last_event_id = max(last_event_id, max(int(e.get("id", 0)) for e in events))
    except Exception as error:  # noqa: BLE001
        log(f"baseline failed: {error}")
        return 1

    title, message = digest(jobs)
    if publish(TOPIC_REPORTS, title, message, ["bar_chart"]):
        log(f"published baseline digest to {TOPIC_REPORTS}")
    previous_jobs = status_snapshot(jobs)
    save_state({"last_event_id": last_event_id, "jobs": previous_jobs})

    while True:
        try:
            payload = get_json(f"/api/logs?since_id={last_event_id}&limit=100")
            events = payload.get("events", [])
            new_max = last_event_id
            for event in events:
                event_id = int(event.get("id", 0))
                if event_id <= last_event_id:
                    continue
                new_max = max(new_max, event_id)
                job_id = event.get("job_id")
                if not job_id:
                    continue
                level = (event.get("level") or "INFO").upper()
                status = event.get("status") or ""
                title = f"Music job {job_id}" + (f" {status}" if status else "")
                message = str(event.get("message", ""))[:MAX_EVENT_CHARS]
                tags = ["musical_note", "warning" if level in {"ERROR", "WARNING"} else "information_source"]
                priority = 4 if level == "ERROR" else 3
                if publish(TOPIC_EVENTS, title, message, tags, priority):
                    log(f"forwarded event {event_id} job={job_id} level={level}")

            if new_max > last_event_id:
                last_event_id = new_max

            jobs = fetch_jobs()
            snapshot = status_snapshot(jobs)
            changed = {k: v for k, v in snapshot.items() if previous_jobs.get(k) != v}
            if changed:
                title, message = digest(jobs)
                if publish(TOPIC_REPORTS, title, message, ["bar_chart"]):
                    log(f"published job-status digest ({len(changed)} changed)")
                previous_jobs = snapshot

            save_state({"last_event_id": last_event_id, "jobs": previous_jobs})
        except Exception as error:  # noqa: BLE001
            log(f"poll failed: {type(error).__name__}: {error}")

        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    sys.exit(main())
