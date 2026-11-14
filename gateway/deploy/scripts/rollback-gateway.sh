#!/usr/bin/env bash
# Roll the gateway back to the previously recorded image, or stop the service.
#
# Persistent state is deliberately preserved: the SQLite volume is not removed.
set -euo pipefail

PROJECT="kaminari-gateway"
COMPOSE_FILE="gateway/deploy/docker-compose.yml"
CURRENT="kaminari-gateway:2.0.0"

log() { printf '[rollback] %s\n' "$*"; }
fail() { printf '[rollback][FAIL] %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || fail "docker is not installed"

if docker image inspect kaminari-gateway:previous >/dev/null 2>&1; then
  log "restoring kaminari-gateway:previous"
  docker image tag kaminari-gateway:previous "$CURRENT"
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --no-build
else
  log "no previous image recorded; stopping the service"
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down
  log "service stopped. The kaminari-gateway-data volume is preserved."
  exit 0
fi

for _ in $(seq 1 30); do
  status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' kaminari-gateway 2>/dev/null || true)
  [ "$status" = "healthy" ] && { log "rollback healthy"; exit 0; }
  sleep 2
done

docker logs --tail 50 kaminari-gateway || true
fail "rolled-back container did not become healthy"
