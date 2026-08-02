#!/usr/bin/env bash
# Provision every staging secret value the fleet needs to boot: JWT material, the
# self-signed origin certificate, database and Redis credentials, and the runtime-config
# bundle. Idempotent — containers are created if missing, values overwritten. Run AFTER
# terraform/rds has applied and staging-urls-secret.sh has stored the endpoint bundle,
# with credentials scoped to the staging account only. Hostnames and the origin SNI are
# read from the STAGING account's Secrets Manager, never from local terraform state:
# this repository is public, and the terraform directories serve prod and staging from
# one checkout, so local init state cannot be trusted to name the right environment.
set -euo pipefail

region=us-west-2
replica_region=ap-northeast-2
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

gen() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32; }

put() { # name file
  aws secretsmanager describe-secret --region "$region" --secret-id "$1" >/dev/null 2>&1 \
    || aws secretsmanager create-secret --region "$region" --name "$1" \
         --add-replica-regions "Region=$replica_region" >/dev/null
  aws secretsmanager put-secret-value --region "$region" --secret-id "$1" \
    --secret-string "file://$2" >/dev/null
  echo "  provisioned  $1"
}

# Guard BEFORE the first write: the bundle carries the account that stored it, and any
# mismatch with the caller means the wrong profile — refuse before provisioning anything.
endpoints_bundle=$(aws secretsmanager get-secret-value --region "$region" \
  --secret-id danteplanner/staging/e2e-endpoints --query SecretString --output text) \
  || { echo "cannot read the endpoint bundle — run staging-urls-secret.sh first" >&2; exit 1; }
caller_account=$(aws sts get-caller-identity --query Account --output text)
bundle_account=$(printf %s "$endpoints_bundle" | jq -r .STAGING_ACCOUNT_ID)
if [ "$caller_account" != "$bundle_account" ]; then
  echo "caller account does not match the account that stored the endpoint bundle —" >&2
  echo "check the profile: this script provisions the staging account only" >&2
  exit 1
fi

# JWT material. The encryption key MUST NOT carry a trailing newline: JwtProperties
# feeds it to Java's strict Base64 decoder, which rejects whitespace and fails boot.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/private.pem" 2>/dev/null
openssl pkey -in "$work/private.pem" -pubout -out "$work/public.pem"
openssl rand -base64 32 | tr -d '\n' > "$work/enc.key"
put danteplanner/jwt/rs256-private-key "$work/private.pem"
put danteplanner/jwt/rs256-public-key  "$work/public.pem"
put danteplanner/jwt/encryption-key    "$work/enc.key"

# Origin TLS: self-signed, SAN = the SNI cloudflared pins (origin_server_name); the
# certificate serves as its own CA on the cloudflared side. Read from the stored bundle
# so the cert cannot drift from the name the tunnel config actually verifies.
api_sni=$(printf %s "$endpoints_bundle" | jq -r .ORIGIN_SERVER_NAME)
openssl req -x509 -newkey rsa:2048 -nodes -days 90 \
  -subj "/CN=${api_sni}" \
  -addext "subjectAltName=DNS:${api_sni}" \
  -keyout "$work/tls.key" -out "$work/tls.crt" 2>/dev/null
python3 - "$work" <<'EOF'
import json, pathlib, sys
d = pathlib.Path(sys.argv[1])
(d/"origin-tls.json").write_text(json.dumps({
  "tls_crt": (d/"tls.crt").read_text(),
  "tls_key": (d/"tls.key").read_text(),
}))
EOF
put danteplanner/origin-tls "$work/origin-tls.json"

# Master password first — terraform/rds reads it at plan time on the NEXT run, and the
# app user bootstrap (staging-e2e-db-users.sh) authenticates with it.
if ! aws secretsmanager describe-secret --region "$region" \
      --secret-id danteplanner/rds/master-password >/dev/null 2>&1; then
  gen > "$work/master.pw"
  put danteplanner/rds/master-password "$work/master.pw"
fi

gen > "$work/app.pw";      printf danteplanner_ro   > "$work/ro.user"
gen > "$work/ro.pw";       printf mysqld_exporter   > "$work/exp.user"
gen > "$work/exp.pw";      gen > "$work/redis.pw";  gen > "$work/stub.secret"
put danteplanner/rds/readonly-username    "$work/ro.user"
put danteplanner/rds/readonly-password    "$work/ro.pw"
put danteplanner/mysqld-exporter/username "$work/exp.user"
put danteplanner/mysqld-exporter/password "$work/exp.pw"

# Credential-scoped read: the assumed staging role decides which account answers, so a
# workstation's terraform init state cannot redirect this to the wrong environment.
endpoint=$(aws rds describe-db-instances --region "$region" --db-instance-identifier danteplanner-mysql \
  --query 'DBInstances[0].Endpoint.Address' --output text)
printf %s "$endpoint" > "$work/primary.endpoint"
put danteplanner/mysqld-exporter/primary-endpoint "$work/primary.endpoint"

replica=$(aws rds describe-db-instances --region "$replica_region" \
  --db-instance-identifier danteplanner-mysql-seoul \
  --query 'DBInstances[0].Endpoint.Address' --output text 2>/dev/null || printf placeholder)
printf %s "$replica" > "$work/replica.endpoint"
put danteplanner/mysqld-exporter/replica-endpoint "$work/replica.endpoint"

# Telemetry credentials are placeholders: staging ships no telemetry until real Grafana
# keys are provisioned by an operator, and the consuming sidecars fail without blocking
# the app pods.
printf placeholder > "$work/ph"
for name in grafana/loki-username grafana/loki-password grafana/remote-write-username \
            grafana/remote-write-password grafana/observability-read-token; do
  put "danteplanner/$name" "$work/ph"
done

# The OAuth and CORS URLs derive from the stored endpoint bundle, the same source the
# suites load — one channel, staging-scoped by the credentials that read it.
ENDPOINTS_JSON="$endpoints_bundle" \
python3 - "$work" "$endpoint" <<'EOF'
import json, os, pathlib, sys
d, endpoint = pathlib.Path(sys.argv[1]), sys.argv[2]
ep = json.loads(os.environ["ENDPOINTS_JSON"])
api, idp, app = ep["E2E_API_URL"], ep["E2E_IDP_URL"], ep["E2E_APP_URL"]
(d/"runtime-config.json").write_text(json.dumps({
  "MYSQL_HOST": endpoint,
  "MYSQL_PORT": "3306",
  "MYSQL_DATABASE": "danteplanner",
  "MYSQL_USER": "danteplanner",
  "MYSQL_PASSWORD": (d/"app.pw").read_text(),
  "AUTH_REDIS_PASSWORD": (d/"redis.pw").read_text(),
  "SENTRY_DSN": "",
  "GOOGLE_OAUTH_CLIENT_ID": "staging-stub",
  "GOOGLE_OAUTH_CLIENT_SECRET": (d/"stub.secret").read_text(),
  "GOOGLE_OAUTH_REDIRECT_URI": f"{api}/api/auth/google/callback",
  "GOOGLE_OAUTH_AUTHORIZE_URL": f"{idp}/auth",
  "GOOGLE_OAUTH_TOKEN_URL": f"{idp}/token",
  "GOOGLE_OAUTH_USER_INFO_URL": f"{idp}/userinfo",
  "CORS_ALLOWEDORIGINS": app,
  # The OAuth negative-case suite spends more callback tokens per run than the
  # production auth bucket holds (5/60s); the loadtest profile is the precedent.
  "RATELIMIT_AUTH_CAPACITY": "1000",
  "RATELIMIT_AUTH_REFILLTOKENS": "1000",
}))
EOF
put danteplanner/backend/runtime-config "$work/runtime-config.json"

# The app-user password must survive for staging-e2e-db-users.sh, which reads it back
# from the bundle rather than receiving it on a command line.
echo "done"
