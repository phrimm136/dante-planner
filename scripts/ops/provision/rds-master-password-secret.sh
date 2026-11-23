#!/usr/bin/env bash
# Seed danteplanner/rds/master-password, which terraform/rds reads at plan time.
# Run BEFORE the rds apply (`ops tf plan rds`, then the printed `! … ops tf apply <plan file>`), and before enrolling the name in
# terraform/secrets secret_names (that stack imports containers, so the name must exist).
#
# Usage:
#   rds-master-password-secret.sh   # prompt for the value
set -euo pipefail

[[ $# -eq 0 ]] || { echo "usage: rds-master-password-secret.sh (takes no arguments; it prompts for the value)" >&2; exit 2; }

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
# shellcheck source=../lib/constants.sh
source "$repo_root/scripts/ops/lib/constants.sh"
name=$SECRET_RDS_MASTER_PASSWORD
region=us-west-2

read -rsp "master password for the RDS primary: " value; echo
[[ -n "$value" ]] || { echo "empty value" >&2; exit 1; }

if ! secret_exists "$region" "$name"; then
  create_secret "$region" "$name"
  echo "  created  $name"
fi

printf %s "$value" | put_secret "$region" "$name"
unset value

echo "done. Next:"
echo "  1. .claude/skills/ops/bin/ops --env <env> tf plan secrets -- -var-file=<env>.tfvars, then the printed ! ... ops tf apply line   # enrols the container for replication"
echo "  2. .claude/skills/ops/bin/ops --env <env> tf plan rds   # must show NO change"
