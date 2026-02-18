#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 10' ERR

# Install Docker if missing
if ! command -v docker &> /dev/null; then
  echo "Installing Docker..."
  yum update -y
  yum install -y docker
  systemctl start docker
  systemctl enable docker
  usermod -aG docker ec2-user
fi

# Install Docker Compose V2 plugin if missing
if ! docker compose version &> /dev/null; then
  echo "Installing Docker Compose V2..."
  DOCKER_CONFIG=${DOCKER_CONFIG:-/usr/local/lib/docker/cli-plugins}
  mkdir -p "$DOCKER_CONFIG"
  curl -SL https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-linux-x86_64 -o "$DOCKER_CONFIG/docker-compose"
  chmod +x "$DOCKER_CONFIG/docker-compose"
fi

# Install Git if missing
if ! command -v git &> /dev/null; then
  echo "Installing Git..."
  yum install -y git
fi

echo "Prerequisites OK"
