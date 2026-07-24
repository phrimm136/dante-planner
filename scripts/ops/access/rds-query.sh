#!/usr/bin/env bash
# Read-only SQL against prod RDS through the SSM tunnel (rds-tunnel.sh).
# Usage: rds-query.sh "SELECT ..." [oregon|seoul]
#        rds-query.sh -f query.sql [oregon|seoul]
# Credentials: danteplanner/rds/readonly-{username,password} in Secrets Manager,
# fetched per invocation — never on disk, never on argv (MYSQL_PWD env only).
# Starts the tunnel if it is down. Client is a throwaway mysql:8 container.
set -euo pipefail

SQL_FILE=""
if [ "${1:-}" = "-f" ]; then SQL_FILE=${2:?usage: rds-query.sh -f query.sql [oregon|seoul]}; shift 2
else SQL=${1:?usage: rds-query.sh \"SELECT ...\" [oregon|seoul]}; shift; fi
SITE=${1:-oregon}
LOCAL_PORT=${RDS_TUNNEL_PORT:-3306}
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Pinned by digest so a poisoned mysql:8 tag can't run with the live credential.
MYSQL_IMAGE="mysql:8@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"

secret() {
  aws secretsmanager get-secret-value --region us-west-2 \
    --secret-id "danteplanner/rds/readonly-$1" \
    --query SecretString --output text
}

"$OPS_DIR/rds-tunnel.sh" status "$SITE" >/dev/null 2>&1 || "$OPS_DIR/rds-tunnel.sh" start "$SITE"

RO_USER=$(secret username)
MYSQL_PWD=$(secret password)
export MYSQL_PWD

run_client() {
  docker run --rm -i --network host -e MYSQL_PWD "$MYSQL_IMAGE" \
    mysql -h 127.0.0.1 -P "$LOCAL_PORT" -u "$RO_USER" \
    --connect-timeout=5 -t danteplanner "$@"
}

if [ -n "$SQL_FILE" ]; then run_client <"$SQL_FILE"; else run_client -e "$SQL"; fi
