#!/usr/bin/env bash
# Restore terraform/cloudflare/staging.tfvars from SSM Parameter Store, which is where the rehearsal
# workflow reads it from and therefore the source of truth. The local file is gitignored
# (it carries an account id and a Cloudflare token), so a deleted one is only recoverable here.
# A stack with a tracked envs/staging.tfvars has no such file and is not restored.
#
# Writes nothing outside terraform/cloudflare/staging.tfvars, and refuses to overwrite an
# existing file unless --force is given: a local edit not yet pushed to SSM would otherwise
# vanish silently.
#
# Usage:
#   AWS_PROFILE=<staging> staging-tfvars-restore.sh [--force]
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
region=${AWS_REGION:-us-west-2}
force=false
[[ "${1:-}" == "--force" ]] && force=true

target="$repo_root/terraform/cloudflare/staging.tfvars"
if [ -f "$target" ] && ! $force; then
  echo "  skip     cloudflare (exists; --force to overwrite)"
  exit 0
fi

if ! value=$(aws ssm get-parameter --region "$region" \
    --name /danteplanner/staging/tfvars/cloudflare --with-decryption \
    --query 'Parameter.Value' --output text 2>/dev/null); then
  echo "  MISSING  cloudflare — no parameter in SSM; this one must be re-authored" >&2
  exit 1
fi

printf %s "$value" > "$target"
chmod 600 "$target"
echo "  restored cloudflare"
