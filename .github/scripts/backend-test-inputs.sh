#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

"$(dirname "$0")/deploy-inputs-changed.sh" --list-inputs
printf '%s\n' backend testdata .claude/skills/static-checkout \
  frontend/package.json frontend/yarn.lock deploy/overlays
