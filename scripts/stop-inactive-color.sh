#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NGINX_CONTAINER="myapp-nginx"
IMAGE_NAME="${IMAGE_NAME:-myapp-member}"
IMAGE_TAG="${IMAGE_TAG:-local}"
COLOR="${1:-}"

if [ "$COLOR" != blue ] && [ "$COLOR" != green ]; then
    echo "Usage: $0 blue|green" >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running or the current user cannot access it." >&2
    exit 1
fi

if [ "$(docker inspect --format '{{.State.Running}}' "$NGINX_CONTAINER" 2>/dev/null || true)" != true ]; then
    echo "Nginx container is not running: $NGINX_CONTAINER" >&2
    exit 1
fi

loaded_config="$(docker exec "$NGINX_CONTAINER" nginx -T 2>&1)"

if grep -Fq "server myapp-member-$COLOR-1:8080" <<< "$loaded_config"; then
    echo "Refusing to stop the active Member color: $COLOR" >&2
    exit 1
fi

if [ "$COLOR" = blue ]; then
    ACTIVE_COLOR=green
else
    ACTIVE_COLOR=blue
fi

if ! grep -Fq "server myapp-member-$ACTIVE_COLOR-1:8080" <<< "$loaded_config"; then
    echo "Could not verify the active Member color." >&2
    exit 1
fi

COMPOSE_FILE="$PROJECT_DIR/deploy/docker-compose-$COLOR.yml"

echo "[1/2] Stop inactive Member containers: $COLOR"
export IMAGE_NAME IMAGE_TAG
docker compose -f "$COMPOSE_FILE" down

echo "[2/2] Verify the active Member environment: $ACTIVE_COLOR"
response="$(docker exec "$NGINX_CONTAINER" \
    wget -q -T 2 -O - http://127.0.0.1/member/hc)"

if ! grep -Fq "\"env\":\"$ACTIVE_COLOR\"" <<< "$response"; then
    echo "Unexpected proxy response: $response" >&2
    exit 1
fi

echo "Proxy response: $response"
echo "Inactive Member color stopped: $COLOR"
