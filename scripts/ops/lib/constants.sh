# shellcheck shell=bash
# Values that must agree across scripts.

# Pinned by digest so a poisoned mysql:8 tag can't run with a live credential.
MYSQL_CLIENT_IMAGE="mysql:8@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"

CLOUDWATCH_NAMESPACE="DantePlanner"

RDS_DEFAULT_PORT=3306

SECRET_RDS_MASTER_PASSWORD="danteplanner/rds/master-password"
SECRET_STAGING_E2E_ENDPOINTS="danteplanner/staging/e2e-endpoints"
SECRET_JWT_BUNDLE="danteplanner/jwt/material"
SECRET_GRAFANA_BUNDLE="danteplanner/grafana/cloud"
SECRET_MYSQLD_EXPORTER_BUNDLE="danteplanner/mysqld-exporter/dsn"
SECRET_RDS_READONLY_BUNDLE="danteplanner/rds/readonly"

AWS_SSO_SESSION="danteplanner"

require_aws_session() {
  aws sts get-caller-identity --query Account --output text >/dev/null 2>&1 && return 0
  {
    echo "No live AWS session${AWS_PROFILE:+ for profile '$AWS_PROFILE'}."
    echo "From a workstation run this through .claude/skills/ops/bin/ops, which names the profile;"
    echo "  .claude/skills/ops/bin/ops doctor reports each profile's session."
    echo "Calling the script directly needs the profile named: AWS_PROFILE=prod $(basename "$0") ..."
    echo "  after: aws sso login --sso-session $AWS_SSO_SESSION"
  } >&2
  return 1
}
