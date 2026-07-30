#!/usr/bin/env bash
# Seed danteplanner/rds/master-password, which terraform/rds reads at plan time.
# Run BEFORE `terraform -chdir=terraform/rds apply`, and before enrolling the name in
# terraform/secrets secret_names (that stack imports containers, so the name must exist).
#
# Usage:
#   rds-master-password-secret.sh              # prompt for the value
#   rds-master-password-secret.sh --from-tfvars  # one-time migration off var.master_password
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
name=danteplanner/rds/master-password
region=us-west-2

if [[ "${1:-}" == "--from-tfvars" ]]; then
  tfvars="${RDS_TFVARS:-$repo_root/terraform/rds/prod.tfvars}"
  [[ -f "$tfvars" ]] || { echo "no $tfvars to migrate from" >&2; exit 1; }
  value=$(awk -F'"' '/^[[:space:]]*master_password[[:space:]]*=/ {print $2}' "$tfvars")
  [[ -n "$value" ]] || { echo "master_password not found in $tfvars" >&2; exit 1; }
  echo "read the current value from terraform.tfvars"
else
  read -rsp "master password for the RDS primary: " value; echo
  [[ -n "$value" ]] || { echo "empty value" >&2; exit 1; }
fi

if ! aws secretsmanager describe-secret --region "$region" --secret-id "$name" >/dev/null 2>&1; then
  aws secretsmanager create-secret --region "$region" --name "$name" >/dev/null
  echo "  created  $name"
fi

# file:///dev/stdin: --secret-string would put the password on the process table.
printf %s "$value" | aws secretsmanager put-secret-value --region "$region" \
  --secret-id "$name" --secret-string file:///dev/stdin >/dev/null
unset value

echo "done. Next:"
echo "  1. terraform -chdir=terraform/secrets apply   # enrols the container for replication"
echo "  2. delete the master_password line from the environment's tfvars"
echo "  3. terraform -chdir=terraform/rds plan -var-file=<env>.tfvars   # must show NO change"
