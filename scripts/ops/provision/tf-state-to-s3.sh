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
state_home="${XDG_STATE_HOME:-$HOME/.local/state}/danteplanner"
backup="$state_home/tf-state-backup"
plans="$state_home/tf-plan-logs"
mkdir -p "$backup" "$plans"
chmod 700 "$backup"
echo "plan output: $plans"

for stack in rds oregon secrets cloudflare seoul global-accelerator; do
  echo
  echo "=== $stack ==="

  # -s, not -f: a migrated stack's local file is left EMPTY, and copying that over a real
  # backup destroys the only pre-migration copy. Re-running this script must not cost anything.
  if [[ -s "$stack/terraform.tfstate" ]]; then
    cp "$stack/terraform.tfstate" "$backup/$stack.tfstate"
    echo "  local state backed up to $backup/$stack.tfstate"
  elif [[ -f "$stack/terraform.tfstate" ]]; then
    echo "  local state is empty (already migrated) — backup left untouched"
  else
    echo "  no local state (nothing to migrate, initialising anyway)"
  fi

  terraform -chdir="$stack" init -migrate-state -backend-config=../backend.hcl

  # Resources in the remote state is the direct test that the migration carried the content;
  # a state that failed to carry over reads as zero, whatever the plan then says.
  resources=$(terraform -chdir="$stack" state list 2>/dev/null | wc -l | tr -d ' ')
  echo "  state: $resources resources in the remote backend"

  plan_log="$plans/$stack.plan"
  if terraform -chdir="$stack" plan -detailed-exitcode -input=false > "$plan_log" 2>&1; then
    echo "  plan: no changes"
  else
    rc=$?
    if [[ $rc -ne 2 ]]; then
      echo "  plan: ERROR (exit $rc) — see $plan_log"
      continue
    fi
    # Exit 2 alone cannot tell an intended config edit from state that did not survive the
    # move. Re-planning without a refresh compares config against state ONLY: clean there
    # means state and config agree and the whole diff is real-world drift.
    if terraform -chdir="$stack" plan -refresh=false -detailed-exitcode -input=false \
        > "$plans/$stack.norefresh.plan" 2>&1; then
      echo "  plan: CHANGES PENDING, all of it drift between state and reality"
    else
      echo "  plan: CHANGES PENDING, config differs from state"
      echo "        (an intended edit on this branch, or content the move dropped —"
      echo "         compare against the resource count above)"
    fi
    echo "        see $plan_log"
  fi
done

echo
echo "done. Local state files are still on disk; delete them only once every stack above"
echo "reports resources in the remote backend and a plan you recognise:"
echo "  aws s3 ls s3://$bucket/ --recursive"
