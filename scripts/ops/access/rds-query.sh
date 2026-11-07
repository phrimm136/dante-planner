#!/usr/bin/env bash
# Read-only SQL against prod RDS through the SSM tunnel (rds-tunnel.sh).
# Usage: rds-query.sh "SELECT ..." [oregon|seoul]
#        rds-query.sh -f query.sql [oregon|seoul]
# Credentials: the danteplanner/rds/readonly bundle {username, password} in Secrets
# Manager, fetched per invocation — never on disk, never on argv (MYSQL_PWD env only).
# Starts the tunnel if it is down. Client is a throwaway mysql:8 container.
set -euo pipefail

SQL_FILE=""
if [ "${1:-}" = "-f" ]; then SQL_FILE=${2:?usage: rds-query.sh -f query.sql [oregon|seoul]}; shift 2
else SQL=${1:?usage: rds-query.sh \"SELECT ...\" [oregon|seoul]}; shift; fi
SITE=${1:-oregon}
LOCAL_PORT=${RDS_TUNNEL_PORT:-3306}
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/../lib/constants.sh"
# shellcheck source=../lib/secrets.sh
source "$OPS_DIR/../lib/secrets.sh"
require_aws_session

BUNDLE=$SECRET_RDS_READONLY_BUNDLE

"$OPS_DIR/rds-tunnel.sh" status "$SITE" >/dev/null 2>&1 || "$OPS_DIR/rds-tunnel.sh" start "$SITE"

CREDS=$(read_bundle us-west-2 "$BUNDLE")
RO_USER=$(bundle_key "$BUNDLE" "$CREDS" username)
MYSQL_PWD=$(bundle_key "$BUNDLE" "$CREDS" password)
export MYSQL_PWD

run_client() {
  docker run --rm -i --network host -e MYSQL_PWD "$MYSQL_CLIENT_IMAGE" \
    mysql -h 127.0.0.1 -P "$LOCAL_PORT" -u "$RO_USER" \
    --connect-timeout=5 -t danteplanner "$@"
}

if [ -n "$SQL_FILE" ]; then run_client <"$SQL_FILE"; else run_client -e "$SQL"; fi
