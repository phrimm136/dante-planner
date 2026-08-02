#!/usr/bin/env bash
# Restore each stack's staging.tfvars from SSM Parameter Store, which is where the rehearsal
# workflow reads them from and therefore the source of truth. The local files are gitignored
# (they carry account ids and a Cloudflare token), so a deleted one is only recoverable here.
#
# Writes nothing outside terraform/<stack>/staging.tfvars, and refuses to overwrite an
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

restored=0
skipped=0
for stack in secrets rds oregon seoul cloudflare; do
  target="$repo_root/terraform/$stack/staging.tfvars"
  if [ -f "$target" ] && ! $force; then
    echo "  skip     $stack (exists; --force to overwrite)"
    skipped=$((skipped + 1))
    continue
  fi

  if ! value=$(aws ssm get-parameter --region "$region" \
      --name "/danteplanner/staging/tfvars/$stack" --with-decryption \
      --query 'Parameter.Value' --output text 2>/dev/null); then
    echo "  MISSING  $stack — no parameter in SSM; this one must be re-authored" >&2
    continue
  fi

  printf %s "$value" > "$target"
  chmod 600 "$target"
  echo "  restored $stack"
  restored=$((restored + 1))
done

echo "done — $restored restored, $skipped skipped"
