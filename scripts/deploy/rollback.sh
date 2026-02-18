#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2' ERR

DEPLOY_DIR="/opt/danteplanner"
cd "$DEPLOY_DIR"

# Restore previous IMAGE_TAG if saved
if [ -f .previous_image_tag ]; then
  PREV_TAG=$(cat .previous_image_tag)
  echo "Restoring previous IMAGE_TAG: $PREV_TAG"
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$PREV_TAG/" .env
else
  echo "WARNING: No previous image tag found, using git rollback only"
  git reset --hard HEAD~1
fi

# Load .env for AWS credentials
# shellcheck source=/dev/null
source .env

# ECR login and restart with previous images
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

docker compose pull
docker compose down --remove-orphans
docker compose up -d --force-recreate

echo "Rollback complete"
