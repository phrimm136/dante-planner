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
[ -n "$PULL_ARGS" ] && docker compose pull $PULL_ARGS

# Rolling update: nginx first (serves 503 during backend restart), then backend
if service_changed "nginx"; then
  docker compose up -d --no-deps --force-recreate nginx
  sleep 2
fi
if service_changed "backend"; then
  # Signal nginx that downtime is planned (not a crash)
  docker exec danteplanner-nginx touch /tmp/maintenance-flag

  docker compose up -d --no-deps --force-recreate backend

  # Wait for backend health, then remove flag. Timeout ensures flag is removed
  # even if backend fails to start, so nginx switches to BACKEND_UNAVAILABLE.
  HEALTHY=false
  for i in $(seq 1 60); do
    if docker exec danteplanner-backend curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
      HEALTHY=true
      break
    fi
    sleep 2
  done

  docker exec danteplanner-nginx rm -f /tmp/maintenance-flag

  if [ "$HEALTHY" = "false" ]; then
    echo "[WARN] Backend did not become healthy within 120s — maintenance flag removed"
  fi
fi

# Cleanup orphaned containers and old images.
# --remove-orphans reaps containers whose service was deleted from compose, but
# never their named volumes — a retained data volume survives the reap.
docker compose up -d --remove-orphans
docker image prune -f --filter "until=720h"  # 30 days

echo "Container deploy complete: $CHANGED_SERVICES"
