#!/usr/bin/env bash
# Roll out one service of the production stack to a new image tag, and roll back if it
# does not become healthy.
#
#   Docker/deploy/deploy.sh <api|web> <image-tag>
#
# Called over SSH by the release workflows of both repositories (see docs/CICD.md).
# The tag each service runs is recorded in deploy/images.env, so a rollback — automatic
# here, or manual by re-running with an older tag — is just another deploy.
set -euo pipefail

SERVICE="${1:?usage: deploy.sh <api|web> <image-tag>}"
TAG="${2:?usage: deploy.sh <api|web> <image-tag>}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"

case "$SERVICE" in
  api) TAG_VAR=API_TAG ;;
  web) TAG_VAR=WEB_TAG ;;
  *) echo "error: unknown service '$SERVICE' (expected api or web)" >&2; exit 2 ;;
esac

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_FILE="$DOCKER_DIR/deploy/images.env"
SECRETS_FILE="$DOCKER_DIR/../.env"
cd "$DOCKER_DIR"

[[ -f "$SECRETS_FILE" ]] || { echo "error: $SECRETS_FILE is missing (copy .env.example)" >&2; exit 1; }
touch "$STATE_FILE"

compose() {
  docker compose --env-file "$SECRETS_FILE" --env-file "$STATE_FILE" -f docker-compose.yml "$@"
}

record_tag() {  # record_tag <tag>
  local tmp
  tmp="$(mktemp)"
  grep -v "^${TAG_VAR}=" "$STATE_FILE" > "$tmp" || true
  echo "${TAG_VAR}=$1" >> "$tmp"
  mv "$tmp" "$STATE_FILE"
}

wait_until_healthy() {
  local container status deadline
  container="$(compose ps -q "$SERVICE")"
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)"
    case "$status" in
      healthy) return 0 ;;
      unhealthy|exited|dead|missing) break ;;
    esac
    sleep 5
  done
  echo "error: $SERVICE did not become healthy (last status: ${status:-unknown})" >&2
  compose logs --tail 80 "$SERVICE" >&2 || true
  return 1
}

PREVIOUS_TAG="$(grep "^${TAG_VAR}=" "$STATE_FILE" | cut -d= -f2- || true)"
echo "==> $SERVICE: ${PREVIOUS_TAG:-<none>} -> $TAG"

# The stores the API needs (no-ops when they are already running).
compose up -d --no-recreate mysql redis influxdb

record_tag "$TAG"
compose pull "$SERVICE"
compose up -d --no-deps "$SERVICE"

if ! wait_until_healthy; then
  if [[ -n "$PREVIOUS_TAG" ]]; then
    echo "==> rolling $SERVICE back to $PREVIOUS_TAG" >&2
    record_tag "$PREVIOUS_TAG"
    compose up -d --no-deps "$SERVICE"
    wait_until_healthy || echo "error: rollback of $SERVICE is not healthy either — intervene by hand" >&2
  fi
  exit 1
fi

# nginx fronts both apps, so it starts once both have been deployed (each repository
# releases on its own). Already running, it is left alone: it resolves the apps per request.
if [[ -n "$(compose ps -q api)" && -n "$(compose ps -q web)" ]]; then
  compose up -d --no-recreate --no-deps nginx
else
  echo "==> nginx starts once both api and web have been deployed"
fi
docker image prune -f --filter "until=168h" >/dev/null
echo "==> $SERVICE is healthy on $TAG"
