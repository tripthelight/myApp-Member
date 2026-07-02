#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="${MYAPP_INFRA_DIR:-$HOME/myApp-Infra}"
IMAGE_NAME="${IMAGE_NAME:-myapp-member}"
IMAGE_TAG="${IMAGE_TAG:-manual-$(date +%Y%m%d%H%M%S)}"
DEPLOY_LOG_DIR="${DEPLOY_LOG_DIR:-$HOME/myapp-deploy-logs/member}"
DEPLOY_RUN_ID="${GITHUB_RUN_ID:-manual}-$(date '+%Y%m%d-%H%M%S')"
LOG_FILE="$DEPLOY_LOG_DIR/deploy-$DEPLOY_RUN_ID.log"

mkdir -p "$DEPLOY_LOG_DIR"
exec > >(tee -a "$LOG_FILE") 2>&1

write_action_output() {
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
    fi
}

write_action_output log_file "$LOG_FILE"

cd "$PROJECT_DIR"

echo "Member image build: $IMAGE_NAME:$IMAGE_TAG"
echo "Deployment log: $LOG_FILE"

echo "[1/3] Build Spring Boot application"
./mvnw --batch-mode --errors -DskipTests clean package

echo "[2/3] Build Docker image"
docker build --tag "$IMAGE_NAME:$IMAGE_TAG" .

echo "[3/3] Deploy through Infra default.conf switcher"
cd "$INFRA_DIR"
IMAGE_OVERRIDE="$IMAGE_NAME:$IMAGE_TAG" ./scripts/deploy-member.sh