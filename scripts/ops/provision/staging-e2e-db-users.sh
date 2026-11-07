#!/usr/bin/env bash
# Create the staging database users (app, readonly, exporter) through the SSM tunnel.
# Credentials are read back from Secrets Manager so no password crosses a command line
# or the SSM command history. Requires: staging-e2e-secrets.sh already ran, the Oregon
# fleet is up (the tunnel rides an app node), and the session-manager-plugin is installed.
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/constants.sh
source "$repo_root/scripts/ops/lib/constants.sh"
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
region=us-west-2

sm() { aws secretsmanager get-secret-value --region "$region" --secret-id "$1" \
        --query SecretString --output text; }

master_pw=$(sm danteplanner/rds/master-password)
app_pw=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["MYSQL_PASSWORD"], end="")' \
  <<<"$(sm danteplanner/backend/runtime-config)")
ro=$(sm "$SECRET_RDS_READONLY_BUNDLE")
ro_user=$(bundle_key "$SECRET_RDS_READONLY_BUNDLE" "$ro" username)
ro_pw=$(bundle_key "$SECRET_RDS_READONLY_BUNDLE" "$ro" password)
exp=$(sm "$SECRET_MYSQLD_EXPORTER_BUNDLE")
exp_user=$(bundle_key "$SECRET_MYSQLD_EXPORTER_BUNDLE" "$exp" username)
exp_pw=$(bundle_key "$SECRET_MYSQLD_EXPORTER_BUNDLE" "$exp" password)

"$repo_root/scripts/ops/access/rds-tunnel.sh" start oregon

docker run --rm -i --network host -e MYSQL_PWD="$master_pw" "$MYSQL_CLIENT_IMAGE" \
  mysql -h 127.0.0.1 -P "${RDS_TUNNEL_PORT:-3306}" -u admin --connect-timeout=15 <<SQL
CREATE USER IF NOT EXISTS 'danteplanner'@'%' IDENTIFIED BY '$app_pw';
GRANT ALL PRIVILEGES ON danteplanner.* TO 'danteplanner'@'%';
CREATE USER IF NOT EXISTS '$ro_user'@'%' IDENTIFIED BY '$ro_pw';
GRANT SELECT, SHOW VIEW ON danteplanner.* TO '$ro_user'@'%';
CREATE USER IF NOT EXISTS '$exp_user'@'%' IDENTIFIED BY '$exp_pw' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO '$exp_user'@'%';
GRANT SELECT ON performance_schema.* TO '$exp_user'@'%';
FLUSH PRIVILEGES;
SQL

echo "database users provisioned"
