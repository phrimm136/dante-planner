#!/usr/bin/env bash
# Stores the staging hostnames and the Cloudflare Access service token in Secrets Manager.
#
# The hostnames are not secrets in the cryptographic sense, but this repository is public and the
# environment they name authenticates through a stub IdP that issues a token to anyone who asks.
# Access guards the hostnames; keeping them out of the repository means an attacker has to find
# them before Access can even be attacked. The service token IS a credential, and the apply that
# creates it is the only chance to read it.
#
# Run after `terraform -chdir=terraform/cloudflare apply -var-file=staging.tfvars`.
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
region=${AWS_REGION:-us-west-2}
name=danteplanner/staging/e2e-endpoints

tf() { terraform -chdir="$repo_root/terraform/cloudflare" "$@"; }

token_json=$(tf output -json access_service_token)
if [[ "$token_json" == "null" ]]; then
  echo "the cloudflare stack reports no access service token: is enable_access true?" >&2
  exit 1
fi

work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
# Built from terraform's own view rather than typed here, so the stored endpoints cannot drift
# from the hostnames Access actually guards.
tf output -json > "$work/tf.json"
# The token travels in the environment, never as an argument: /proc/PID/cmdline is world-readable
# so an argument shows up in anyone's `ps`, while /proc/PID/environ is readable only by its owner.
# Stdin is already taken by the heredoc carrying this script.
TOKEN_JSON="$token_json" ENDPOINTS_JSON="$(tf output -json e2e_endpoints)" \
ORIGIN_SNI="$(tf output -raw origin_server_name)" \
WRITER_ACCOUNT="$(aws sts get-caller-identity --query Account --output text)" python3 - "$work" <<'EOF'
import json, os, pathlib, sys
work = pathlib.Path(sys.argv[1])
# Endpoints come from terraform, whose tfvars is untracked, so no hostname is written here.
# This script is the ONLY terraform-output reader: it runs paired with the staging apply,
# so its init state is staging by contract; every other consumer reads the stored bundle.
bundle = json.loads(os.environ["ENDPOINTS_JSON"])
if not bundle:
    raise SystemExit("terraform reports no e2e_endpoints: set it in the environment's tfvars")
token = json.loads(os.environ["TOKEN_JSON"])
bundle["CF_ACCESS_CLIENT_ID"] = token["client_id"]
bundle["CF_ACCESS_CLIENT_SECRET"] = token["client_secret"]
bundle["ORIGIN_SERVER_NAME"] = os.environ["ORIGIN_SNI"]
# Consumers compare this against their own caller identity, so credentials pointed at
# the wrong account fail loudly instead of provisioning it.
bundle["STAGING_ACCOUNT_ID"] = os.environ["WRITER_ACCOUNT"]
(work / "bundle.json").write_text(json.dumps(bundle, indent=2))
EOF

ensure_secret "$region" "$name"
put_secret_file "$region" "$name" "$work/bundle.json"

echo "stored $name"
echo "load it into a shell with: eval \"\$($repo_root/scripts/ops/access/staging-env.sh)\""
