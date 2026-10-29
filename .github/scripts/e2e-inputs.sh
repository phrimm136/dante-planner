#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

printf '%s\n' e2e static.lock \
  docker-compose.yml docker-compose.local.yml docker-compose.e2e.yml \
  docker-compose.oauth-gtid.yml docker-compose.multiregion.yml \
  nginx redis scripts/ops/local-multiregion-up.sh
