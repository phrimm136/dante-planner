#!/usr/bin/env bash
# One-time: create/converge the read-only MySQL user on the PRIMARY by running
# rds-readonly-user.sql with the Secrets Manager password substituted for
# REPLACE_ME (env-only transit, the file on disk is untouched). Admin (master)
# credentials are fetched, not prompted: username from the RDS API, password
# from the gitignored terraform/rds/terraform.tfvars (its declared home — the
# AWS-managed master secret is disabled for cross-region replica support).
# Run provision/rds-readonly-secrets.sh first.
# Usage: rds-bootstrap-readonly-user.sh
set -euo pipefail
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TFVARS="$OPS_DIR/../../terraform/rds/terraform.tfvars"
# Pinned by digest — a floating tag would let a poisoned mysql:8 push run with
# the live master credential this script hands it. Re-pin on a deliberate bump.
MYSQL_IMAGE="mysql:8@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"

# Bring the tunnel up BEFORE the secrets exist in this environment, so the
# long-lived ssm start-session child never inherits them via /proc/<pid>/environ.
"$OPS_DIR/access/rds-tunnel.sh" status oregon >/dev/null 2>&1 \
  || "$OPS_DIR/access/rds-tunnel.sh" start oregon

TFVARS_MODE=$(stat -c %a "$TFVARS")
[ "${TFVARS_MODE: -2}" = "00" ] || { echo "refusing: $TFVARS is group/world-readable ($TFVARS_MODE) — chmod 600"; exit 1; }

PW=$(aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id danteplanner/rds/readonly-password \
  --query SecretString --output text)
[ -n "$PW" ] && [ "$PW" != "None" ] || { echo "readonly-password secret is empty"; exit 1; }
ADMIN_USER=$(aws rds describe-db-instances --region us-west-2 \
  --db-instance-identifier danteplanner-mysql \
  --query "DBInstances[0].MasterUsername" --output text)
MYSQL_PWD=$(aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id danteplanner/rds/master-password --query SecretString --output text)
[ -n "$MYSQL_PWD" ] || { echo "danteplanner/rds/master-password is empty or unreadable"; exit 1; }
export PW MYSQL_PWD

perl -pe 's/REPLACE_ME/$ENV{PW}/g' "$OPS_DIR/rds-readonly-user.sql" |
  docker run --rm -i --network host -e MYSQL_PWD "$MYSQL_IMAGE" \
    mysql -h 127.0.0.1 -P "${RDS_TUNNEL_PORT:-3306}" -u "$ADMIN_USER"

echo "read-only user converged — verify: scripts/ops/access/rds-query.sh \"SELECT CURRENT_USER()\""
