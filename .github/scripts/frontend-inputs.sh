#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

printf '%s\n' frontend .claude/skills/static-checkout static.lock testdata
