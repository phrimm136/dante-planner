#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 30' ERR

DEPLOY_DIR="/opt/danteplanner"
cd "$DEPLOY_DIR"

# Load .env for AWS credentials
# shellcheck source=/dev/null
source .env

# ECR login
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

docker compose build mysql
docker compose pull --ignore-buildable

# Rolling Update: nginx first (serves maintenance page), then backend
# Minimizes downtime - nginx stays up to respond with 503 during backend restart
docker compose up -d --no-deps --force-recreate nginx
sleep 2
docker compose up -d --no-deps --force-recreate backend
docker compose up -d --no-deps mysql

# Cleanup orphaned containers and old images
docker compose up -d --remove-orphans
docker image prune -f --filter "until=720h"  # 30 days

echo "Container deploy complete"
