# shellcheck shell=bash
# Values that must agree across scripts.

# Pinned by digest so a poisoned mysql:8 tag can't run with a live credential.
MYSQL_CLIENT_IMAGE="mysql:8@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"

DEPLOY_DIR="/opt/danteplanner"

CLOUDWATCH_NAMESPACE="DantePlanner"

RDS_DEFAULT_PORT=3306

SECRET_RDS_MASTER_PASSWORD="danteplanner/rds/master-password"
SECRET_RDS_READONLY_USERNAME="danteplanner/rds/readonly-username"
SECRET_RDS_READONLY_PASSWORD="danteplanner/rds/readonly-password"
SECRET_STAGING_E2E_ENDPOINTS="danteplanner/staging/e2e-endpoints"
