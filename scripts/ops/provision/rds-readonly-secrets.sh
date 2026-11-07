#!/usr/bin/env bash
# Write the read-only RDS credential bundle danteplanner/rds/readonly {username, password} for
# rds-query.sh. The container is created with its Seoul replica when absent (terraform/secrets
# secret_names then adopts it by import); values go to the PRIMARY region and AWS replicates them.
# The password is generated in-process and stored without ever being displayed,
# typed, or written to disk — its only consumers read it from Secrets Manager.
# Usage: rds-readonly-secrets.sh             (username prompted, password generated)
#        rds-readonly-secrets.sh --migrate   (copies the two single-value containers, no prompt)
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
# shellcheck source=../lib/constants.sh
source "$repo_root/scripts/ops/lib/constants.sh"
region=us-west-2
replica_region=ap-northeast-2
name=$SECRET_RDS_READONLY_BUNDLE

old() { aws secretsmanager get-secret-value --region "$region" --secret-id "$1" --output json; }

case "${1:-}" in
  --migrate)
    bundle=$(
      {
        old danteplanner/rds/readonly-username
        old danteplanner/rds/readonly-password
      } | jq -cs 'if length != 2 or any(.[]; .SecretString | type != "string")
                  then error("expected two string secrets") else
                  {username: .[0].SecretString, password: .[1].SecretString} end'
    )
    ;;
  "")
    read -rp "Read-only MySQL username [danteplanner_ro]: " RO_USER
    RO_USER=${RO_USER:-danteplanner_ro}
    RO_PASS=$(openssl rand -base64 24)
    bundle=$(RO_USER=$RO_USER RO_PASS=$RO_PASS jq -nc '{username: env.RO_USER, password: env.RO_PASS}')
    ;;
  *) echo "usage: rds-readonly-secrets.sh [--migrate]" >&2; exit 2 ;;
esac

if ! secret_exists "$region" "$name"; then
  create_secret "$region" "$name" --add-replica-regions "Region=$replica_region"
  echo "  created  $name  (with its $replica_region replica)"
fi
printf %s "$bundle" | put_secret "$region" "$name"
echo "  updated  $name  (replicates to $replica_region)"
echo "done — verify: scripts/ops/access/rds-query.sh \"SELECT CURRENT_USER()\""
