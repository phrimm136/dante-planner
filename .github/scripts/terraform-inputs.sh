#!/bin/sh
set -eu

if [ "${1:-}" != "--list-inputs" ]; then
  echo "usage: $0 --list-inputs" >&2
  exit 2
fi

printf '%s\n' terraform scripts/ops/terraform-run.sh scripts/ops/lib/terraform-guard.sh scripts/ops/provision \
  scripts/ops/tests scripts/ops/fleet-verify.sh \
  scripts/ops/lib/common.sh scripts/ops/lib/constants.sh scripts/ops/lib/etcd-interval.sh \
  .github/scripts/terraform-validate.sh .github/scripts/terraform-inputs.sh .github/workflows/pr-gate.yml \
  .github/scripts/config-scan.sh .github/scripts/render-charts.sh .github/scripts/images.sh .github/config-scan \
  .github/scripts/deploy-inputs-changed.sh .github/workflows/deploy-fleet.yml deploy/argocd \
  deploy/chart/values-oregon.yaml deploy/chart/values-seoul.yaml
