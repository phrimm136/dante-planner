#!/usr/bin/env bash
# Move each stack's Terraform state from the local disk into the shared S3 bucket.
#
# Run once, after `terraform -chdir=terraform/iam-bootstrap apply` has created the bucket.
# iam-bootstrap itself stays on local state: it creates the bucket the others use, so it has
# to be appliable with nothing but credentials.
#
# Order matters for the stacks that read each other. `init` does not evaluate data sources, so
# migration order is free, but a `plan` on seoul or global-accelerator resolves oregon/rds
# state over S3 — so those two go last and only make sense once their upstreams have landed.
#
# terraform prompts before each copy. That is deliberate: answer yes per stack, having read
# what it says it will move.
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
cd "$repo_root/terraform"

if [[ ! -f backend.hcl ]]; then
  echo "terraform/backend.hcl is missing — copy backend.hcl.example and set the bucket:" >&2
  echo "  terraform -chdir=iam-bootstrap output -raw tf_state_bucket" >&2
  exit 1
fi

bucket=$(awk -F'"' '/^ *bucket/ {print $2}' backend.hcl)
[[ -n "$bucket" ]] || { echo "no bucket set in terraform/backend.hcl" >&2; exit 1; }
echo "target bucket: $bucket"

if ! aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
  echo "bucket $bucket is not reachable — apply terraform/iam-bootstrap first" >&2
  exit 1
fi

# Outside the repo: state carries the RDS master password and the tunnel secrets in plaintext.
backup="${XDG_STATE_HOME:-$HOME/.local/state}/danteplanner/tf-state-backup"
mkdir -p "$backup"
chmod 700 "$backup"

for stack in rds oregon secrets cloudflare seoul global-accelerator; do
  echo
  echo "=== $stack ==="

  if [[ -f "$stack/terraform.tfstate" ]]; then
    cp "$stack/terraform.tfstate" "$backup/$stack.tfstate"
    echo "  local state backed up to $backup/$stack.tfstate"
  else
    echo "  no local state (nothing to migrate, initialising anyway)"
  fi

  terraform -chdir="$stack" init -migrate-state -backend-config=../backend.hcl

  # A migrated stack must see no changes. Anything else means the state did not carry over.
  if terraform -chdir="$stack" plan -detailed-exitcode -input=false >/dev/null 2>&1; then
    echo "  plan: no changes"
  else
    case $? in
      2) echo "  plan: CHANGES PENDING — inspect before continuing" ;;
      *) echo "  plan: ERROR — inspect before continuing" ;;
    esac
  fi
done

echo
echo "done. Local state files are still on disk; delete them only once every stack above"
echo "reports no changes and you have confirmed the objects exist:"
echo "  aws s3 ls s3://$bucket/ --recursive"
