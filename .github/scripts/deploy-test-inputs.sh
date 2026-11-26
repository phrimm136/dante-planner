#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

printf '%s\n' deploy .github/deploy-test terraform/modules/fleet/variables.tf scripts/ops/lib/etcd-interval.sh \
  .github/scripts/deploy-test.sh .github/scripts/deploy-test-inputs.sh \
  .github/scripts/config-scan.sh .github/scripts/render-charts.sh .github/scripts/rendered-crds.py .github/scripts/rendered-exporters.py .github/scripts/rendered-redis-probes.py .github/scripts/images.sh .github/config-scan
