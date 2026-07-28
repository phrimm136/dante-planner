#!/usr/bin/env bash
# Inject the cloudflared tunnel token VALUES, read from terraform/cloudflare's outputs. Run after
# `terraform -chdir=terraform/cloudflare apply` and before the cluster syncs cloudflared.
# Creates the container on first run (then enrolled in terraform/secrets secret_names).
# Usage: cloudflare-tunnel-secrets.sh
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
name=danteplanner/cloudflare/tunnel-tokens
region=us-west-2

tokens=$(terraform -chdir="$repo_root/terraform/cloudflare" output -json tunnel_tokens)

for r in oregon seoul; do
  if [ "$(printf %s "$tokens" | jq -r --arg r "$r" '.[$r] // empty')" = "" ]; then
    echo "no tunnel token for $r — apply terraform/cloudflare first" >&2
    exit 1
  fi
done

if ! aws secretsmanager describe-secret --region "$region" --secret-id "$name" >/dev/null 2>&1; then
  aws secretsmanager create-secret --region "$region" --name "$name" >/dev/null
  echo "  created  $name  (already enrolled in terraform/secrets secret_names; apply for replication)"
fi

# file:///dev/stdin: --secret-string would put the token on the process table.
printf %s "$tokens" | aws secretsmanager put-secret-value --region "$region" \
  --secret-id "$name" --secret-string file:///dev/stdin >/dev/null

echo "done — the ExternalSecret refreshes hourly; force it with:"
echo "  kubectl -n danteplanner delete secret cloudflare-tunnel-token"
echo "then confirm >=4 edge connections per tunnel in the Cloudflare dashboard."
