#!/usr/bin/env bash
# Copy every danteplanner/* secret VALUE from one account's Secrets Manager to another's,
# preserving names and cross-region replica configuration. The source is never modified:
# migration keeps the origin account intact as the rollback path, so this copies, never moves.
#
# Terraform owns the secret CONTAINERS it manages; values never transit terraform state, so
# moving values between accounts is imperative work by design (same boundary as the other
# provision scripts).
#
# Usage:
#   SRC_PROFILE=<source> DST_PROFILE=<destination> copy-secrets-across-accounts.sh            # dry run
#   SRC_PROFILE=<source> DST_PROFILE=<destination> copy-secrets-across-accounts.sh --execute
set -euo pipefail

region=${AWS_REGION:-us-west-2}
prefix=danteplanner/
execute=false
[[ "${1:-}" == "--execute" ]] && execute=true

: "${SRC_PROFILE:?set SRC_PROFILE to the source account's profile}"
: "${DST_PROFILE:?set DST_PROFILE to the destination account's profile}"

src() { aws --profile "$SRC_PROFILE" --region "$region" "$@"; }
dst() { aws --profile "$DST_PROFILE" --region "$region" "$@"; }

# Same-account runs would silently no-op half the copies and clobber the other half.
src_account=$(src sts get-caller-identity --query Account --output text)
dst_account=$(dst sts get-caller-identity --query Account --output text)
if [ "$src_account" = "$dst_account" ]; then
  echo "source and destination resolve to the same account — check the profiles" >&2
  exit 1
fi

names=$(src secretsmanager list-secrets \
  --filters "Key=name,Values=$prefix" \
  --query 'SecretList[].Name' --output text | tr '\t' '\n' | sort)
[ -n "$names" ] || { echo "no secrets under $prefix in the source account" >&2; exit 1; }

copied=0
for name in $names; do
  replicas=$(src secretsmanager describe-secret --secret-id "$name" \
    --query 'ReplicationStatus[].Region' --output text | tr '\t' ' ')

  if ! $execute; then
    exists="create"
    dst secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1 && exists="overwrite"
    echo "would copy  $name  (replicas: ${replicas:-none}; destination: $exists)"
    continue
  fi

  if ! dst secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
    create_args=(--name "$name")
    for r in $replicas; do create_args+=(--add-replica-regions "Region=$r"); done
    dst secretsmanager create-secret "${create_args[@]}" >/dev/null
  fi

  # Value rides stdin, never argv: /proc/PID/cmdline is world-readable.
  src secretsmanager get-secret-value --secret-id "$name" \
    --query SecretString --output text \
    | dst secretsmanager put-secret-value --secret-id "$name" \
        --secret-string file:///dev/stdin >/dev/null
  copied=$((copied + 1))
  echo "  copied  $name"
done

if $execute; then
  echo "done — $copied value(s) copied; the source account is unchanged"
else
  echo
  echo "dry run only — re-run with --execute to copy"
fi
