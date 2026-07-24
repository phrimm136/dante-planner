#!/usr/bin/env bash
# Inject the read-only RDS credential VALUES for rds-query.sh. Creates the
# secret containers on first run (they are then enrolled in terraform/secrets
# secret_names, whose declarative import adopts existing containers only);
# values are written to the PRIMARY region and replicated by AWS.
# The password is generated in-process and stored without ever being displayed,
# typed, or written to disk — its only consumers read it from Secrets Manager.
# Usage: rds-readonly-secrets.sh
set -euo pipefail

read -rp "Read-only MySQL username [danteplanner_ro]: " RO_USER
RO_USER=${RO_USER:-danteplanner_ro}
RO_PASS=$(openssl rand -base64 24)

put() { # name value
  local name=$1 value=$2
  if ! aws secretsmanager describe-secret --region us-west-2 --secret-id "$name" >/dev/null 2>&1; then
    aws secretsmanager create-secret --region us-west-2 --name "$name" >/dev/null
    echo "  created  $name  (enroll it in terraform/secrets secret_names, then apply for replication)"
  fi
  printf %s "$value" | aws secretsmanager put-secret-value --region us-west-2 \
    --secret-id "$name" --secret-string file:///dev/stdin >/dev/null
  echo "  updated  $name"
}

put danteplanner/rds/readonly-username "$RO_USER"
put danteplanner/rds/readonly-password "$RO_PASS"
echo "done — verify: scripts/ops/access/rds-query.sh \"SELECT CURRENT_USER()\""
