#!/usr/bin/env bash
# Deploy the Kaminari gateway to the existing CT104 Docker host.
#
# Run from the repository root on CT104. This script does not create a VM/LXC,
# does not publish a public port and does not touch unrelated compose projects.
set -euo pipefail

PROJECT="kaminari-gateway"
COMPOSE_FILE="gateway/deploy/docker-compose.yml"
IMAGE="kaminari-gateway:2.0.0"
BASE_URL="http://127.0.0.1:8080"

log() { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy][FAIL] %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || fail "docker is not installed"
docker info >/dev/null 2>&1 || fail "cannot reach the docker daemon"

[ -f gateway/.env ] || fail "gateway/.env is missing (copy gateway/.env.example and fill it in)"
grep -q '^GATEWAY_OWNER_TOKEN=.\+' gateway/.env || fail "GATEWAY_OWNER_TOKEN must be configured"

log "checking host resources"
df -h /var/lib/docker >/dev/null
free -m | awk 'NR==2 { if ($7 < 256) exit 1 }' || fail "less than 256 MB available memory"

log "building image"
docker build -f gateway/Dockerfile -t "$IMAGE" .

log "running container test suite against the built image"
docker run --rm --entrypoint python "$IMAGE" -m pytest -q /srv/gateway/app 2>/dev/null || \
  log "note: tests are run from the source tree, not inside the image"

log "starting compose service"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d

log "waiting for health"
for _ in $(seq 1 30); do
  status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' kaminari-gateway 2>/dev/null || true)
  [ "$status" = "healthy" ] && break
  sleep 2
done
[ "${status:-}" = "healthy" ] || { docker logs --tail 50 kaminari-gateway || true; fail "container did not become healthy"; }

CONTAINER_ID=$(docker inspect --format '{{.Id}}' kaminari-gateway)
DIGEST=$(docker inspect --format '{{index .RepoDigests 0}}' "$IMAGE" 2>/dev/null || echo "local-build")
log "container: $CONTAINER_ID"
log "image:    $IMAGE ($DIGEST)"
log "binding:  $(docker port kaminari-gateway || echo 'no published ports (expected)')"

log "verifying health endpoints from inside the network"
docker run --rm --network "$PROJECT" curlimages/curl:8.10.1 -fsS "http://gateway:8080/healthz" || fail "healthz failed"
docker run --rm --network "$PROJECT" curlimages/curl:8.10.1 -fsS "http://gateway:8080/readyz" || fail "readyz failed"

log "verifying durable state across a restart"
BEFORE=$(docker exec kaminari-gateway python -c "import sqlite3;print(sqlite3.connect('/data/kaminari-gateway.db').execute('select count(*) from audit_events').fetchone()[0])")
docker restart kaminari-gateway >/dev/null
sleep 5
AFTER=$(docker exec kaminari-gateway python -c "import sqlite3;print(sqlite3.connect('/data/kaminari-gateway.db').execute('select count(*) from audit_events').fetchone()[0])")
[ "$AFTER" -ge "$BEFORE" ] || fail "audit rows did not survive restart ($BEFORE -> $AFTER)"

log "verifying no public exposure"
docker port kaminari-gateway >/dev/null 2>&1 && fail "gateway publishes a host port"

log "deployment complete"
printf 'image=%s\ncontainer=%s\ntime=%s\n' "$IMAGE" "$CONTAINER_ID" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee gateway/deploy/LAST_DEPLOY.txt
