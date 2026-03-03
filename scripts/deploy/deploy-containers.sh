#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 30' ERR

DEPLOY_DIR="/opt/danteplanner"
cd "$DEPLOY_DIR"

CHANGED_SERVICES="${1:-}"

if [ -z "$CHANGED_SERVICES" ]; then
  echo "No services to update, skipping container deploy"
  exit 0
fi

service_changed() { echo "$CHANGED_SERVICES" | tr ',' '\n' | grep -qx "$1"; }

# Load .env for AWS credentials
# shellcheck source=/dev/null
source .env

# ECR login
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Pull only changed services from ECR
PULL_ARGS=""
service_changed "backend" && PULL_ARGS="$PULL_ARGS backend"
service_changed "nginx"   && PULL_ARGS="$PULL_ARGS nginx"
service_changed "mysql"   && PULL_ARGS="$PULL_ARGS mysql"
[ -n "$PULL_ARGS" ] && docker compose pull $PULL_ARGS

# Rolling update: nginx first (serves 503 during backend restart), then backend, then mysql
if service_changed "nginx"; then
  docker compose up -d --no-deps --force-recreate nginx
  sleep 2
fi
service_changed "backend" && docker compose up -d --no-deps --force-recreate backend
service_changed "mysql"   && docker compose up -d --no-deps --force-recreate mysql

# Cleanup orphaned containers and old images
docker compose up -d --remove-orphans
docker image prune -f --filter "until=720h"  # 30 days

echo "Container deploy complete: $CHANGED_SERVICES"
