#!/usr/bin/env bash
# Inject the read-only RDS credential VALUES for rds-query.sh. Creates the
# secret containers on first run (they are then enrolled in terraform/secrets
# secret_names, whose declarative import adopts existing containers only);
# values are written to the PRIMARY region and replicated by AWS.
# The password is generated in-process and stored without ever being displayed,
# typed, or written to disk — its only consumers read it from Secrets Manager.
# Usage: rds-readonly-secrets.sh
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
region=us-west-2

read -rp "Read-only MySQL username [danteplanner_ro]: " RO_USER
RO_USER=${RO_USER:-danteplanner_ro}
RO_PASS=$(openssl rand -base64 24)

put() { # name value
  local name=$1 value=$2
  if ! secret_exists "$region" "$name"; then
    create_secret "$region" "$name"
    echo "  created  $name  (enroll it in terraform/secrets secret_names, then apply for replication)"
  fi
  printf %s "$value" | put_secret "$region" "$name"
  echo "  updated  $name"
}

put danteplanner/rds/readonly-username "$RO_USER"
put danteplanner/rds/readonly-password "$RO_PASS"
echo "done — verify: scripts/ops/access/rds-query.sh \"SELECT CURRENT_USER()\""
