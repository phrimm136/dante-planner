#!/usr/bin/env bash
# Copy the CURRENT terraform state objects from one account's tfstate bucket to another's.
# The source bucket is never modified: migration keeps the origin account intact as the
# rollback path, so this copies, never moves. Version history stays in the source — only
# the live objects travel.
#
# Two hops through a local scratch directory, because a single-hop s3-to-s3 copy needs one
# principal with rights in both accounts, which the per-account isolation deliberately
# forbids. Bucket names come from the two backend config files (gitignored — they carry
# account ids), so the copy targets exactly what terraform init would.
#
# NOTE for stacks whose RESOURCES live in the source AWS account (rds, oregon, seoul,
# secrets): copied state describes resources the destination account cannot manage; it is
# history, not a re-homing. Only a stack with no AWS-resident resources (cloudflare) can
# re-init against the copied state and carry on.
#
# Usage:
#   SRC_PROFILE=<source> DST_PROFILE=<destination> \
#     copy-state-across-accounts.sh <src-backend.hcl> <dst-backend.hcl>            # dry run
#   ... copy-state-across-accounts.sh <src-backend.hcl> <dst-backend.hcl> --execute
set -euo pipefail

: "${SRC_PROFILE:?set SRC_PROFILE to the source account's profile}"
: "${DST_PROFILE:?set DST_PROFILE to the destination account's profile}"
src_hcl=${1:?first argument: source backend hcl file}
dst_hcl=${2:?second argument: destination backend hcl file}
execute=false
[[ "${3:-}" == "--execute" ]] && execute=true

bucket_of() { sed -n 's/^bucket[[:space:]]*=[[:space:]]*"\(.*\)"/\1/p' "$1"; }
src_bucket=$(bucket_of "$src_hcl")
dst_bucket=$(bucket_of "$dst_hcl")
[ -n "$src_bucket" ] || { echo "no bucket line in $src_hcl" >&2; exit 1; }
[ -n "$dst_bucket" ] || { echo "no bucket line in $dst_hcl" >&2; exit 1; }
if [ "$src_bucket" = "$dst_bucket" ]; then
  echo "source and destination name the same bucket — check the backend files" >&2
  exit 1
fi

if ! $execute; then
  echo "would copy s3://$src_bucket -> s3://$dst_bucket, objects:"
  aws --profile "$SRC_PROFILE" s3 ls "s3://$src_bucket" --recursive
  echo
  echo "dry run only — re-run with --execute to copy"
  exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Lock objects are excluded: a copied lock would wedge the first init in the destination.
aws --profile "$SRC_PROFILE" s3 sync "s3://$src_bucket" "$work" --exclude "*.tflock"
aws --profile "$DST_PROFILE" s3 sync "$work" "s3://$dst_bucket" --exclude "*.tflock"

echo "done — s3://$src_bucket copied to s3://$dst_bucket; the source bucket is unchanged"
