#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

printf '%s\n' terraform scripts/ops/terraform-run.sh scripts/ops/lib/terraform-guard.sh \
  .github/scripts/terraform-validate.sh .github/scripts/terraform-inputs.sh .github/workflows/pr-gate.yml
