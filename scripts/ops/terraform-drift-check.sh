#!/usr/bin/env bash
# Nightly terraform drift detection (stage 1: detect only).
#
# Covers the stacks in STACKS below. terraform/iam-bootstrap and terraform/state-backend keep
# their state in S3 too but have no inputs the workflow can supply, so check those two by hand
# with `ops tf plan`.
#
# Reports ONLY stack names and change counts — the repo is public, so plan bodies (resource
# ARNs, topology) never reach the issue. Plan logs stay on the machine that ran the check and
# are never uploaded; reproduce locally to see why a stack failed.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
GH_REPO="phrimm136/dante-planner"
ISSUE_LABEL="terraform-drift"
STACKS=(oregon seoul rds secrets cloudflare)

# Every stack without an envs/ directory names its var-file; a stack with one takes envs/prod.tfvars
# and, when it declares aws_account_id, the caller's account, as `ops tf plan` does. A stack with
# neither is a configuration error rather than a stack that needs no inputs, so the loop below
# refuses to plan it: a plan missing an input that gates a resource reports that resource's
# destruction as drift.
declare -A VAR_FILE=(
  [oregon]=prod.tfvars
  [seoul]=prod.tfvars
  [secrets]=prod.tfvars
  [cloudflare]=prod.tfvars
)
caller_account=""

# Stacks that keep more than one workspace. The default workspace holds different resources, so
# planning without selecting reports the whole stack as absent.
declare -A WORKSPACE=( [cloudflare]=prod-fleet )

BACKEND_CONFIG="${BACKEND_CONFIG:-$REPO_DIR/terraform/backend.prod.hcl}"

drifted=()
failed=()
summary=""

for stack in "${STACKS[@]}"; do
  dir="$REPO_DIR/terraform/$stack"
  [ -d "$dir" ] || continue
  log="/tmp/tf-drift-$stack-$(date +%Y%m%d).log"

  args=(-detailed-exitcode -input=false -lock=false -no-color)
  if [ -d "$dir/envs" ]; then
    if [ ! -f "$dir/envs/prod.tfvars" ]; then
      failed+=("$stack")
      summary+="- \`$stack\`: no envs/prod.tfvars — refusing to plan without its settings"$'\n'
      continue
    fi
    args+=(-var-file=envs/prod.tfvars)
    if grep -Eq '^[[:space:]]*variable[[:space:]]+"aws_account_id"' "$dir"/*.tf; then
      if [ -z "$caller_account" ]; then
        caller_account=$(aws sts get-caller-identity --query Account --output text 2>/dev/null) || caller_account=""
      fi
      if [ -z "$caller_account" ]; then
        failed+=("$stack")
        summary+="- \`$stack\`: the caller's account is unreadable (aws sts get-caller-identity failed)"$'\n'
        continue
      fi
      args+=(-var "aws_account_id=$caller_account")
    fi
  elif [ -n "${VAR_FILE[$stack]:-}" ]; then
    args+=(-var-file="${VAR_FILE[$stack]}")
  else
    failed+=("$stack")
    summary+="- \`$stack\`: no VAR_FILE entry — refusing to plan without an explicit var-file"$'\n'
    continue
  fi

  if [ ! -d "$dir/.terraform" ]; then
    "$REPO_DIR/scripts/ops/terraform-run.sh" -chdir="$dir" init -input=false -no-color \
      -backend-config="$BACKEND_CONFIG" >"$log" 2>&1 || {
      failed+=("$stack")
      summary+="- \`$stack\`: init FAILED (log: $log)"$'\n'
      continue
    }
  fi

  if [ -n "${WORKSPACE[$stack]:-}" ]; then
    if ! "$REPO_DIR/scripts/ops/terraform-run.sh" -chdir="$dir" workspace select "${WORKSPACE[$stack]}" >>"$log" 2>&1; then
      failed+=("$stack")
      summary+="- \`$stack\`: workspace ${WORKSPACE[$stack]} select FAILED (log: $log)"$'\n'
      continue
    fi
  fi

  "$REPO_DIR/scripts/ops/terraform-run.sh" -chdir="$dir" plan "${args[@]}" >"$log" 2>&1
  rc=$?
  case "$rc" in
    0) ;;
    2)
      drifted+=("$stack")
      counts=$(grep -Eo 'Plan: [0-9]+ to add, [0-9]+ to change, [0-9]+ to destroy' "$log" | tail -1)
      summary+="- \`$stack\`: ${counts:-diff present}"$'\n'
      ;;
    *)
      failed+=("$stack")
      summary+="- \`$stack\`: plan FAILED rc=$rc"$'\n'
      ;;
  esac
done

if [ "${#drifted[@]}" -eq 0 ] && [ "${#failed[@]}" -eq 0 ]; then
  echo "no drift across ${#STACKS[@]} stacks"
  exit 0
fi

if [ -n "${GITHUB_RUN_ID:-}" ]; then
  origin="workflow run https://github.com/$GH_REPO/actions/runs/$GITHUB_RUN_ID"
else
  origin="$(hostname)"
fi

title="terraform drift: ${drifted[*]:-} ${failed[*]:+(plan failures: ${failed[*]})}"
body="Drift check from $origin at $(date -Iseconds).

$summary
Counts only — plan output is never uploaded. Reproduce locally for the reason:
\`.claude/skills/ops/bin/ops tf plan <stack>\`"

existing=$(gh issue list --repo "$GH_REPO" --label "$ISSUE_LABEL" --state open \
  --json number --jq '.[0].number' 2>/dev/null)
if [ -n "$existing" ] && [ "$existing" != "null" ]; then
  gh issue comment "$existing" --repo "$GH_REPO" --body "$body"
else
  gh label create "$ISSUE_LABEL" --repo "$GH_REPO" --description "Nightly terraform drift reports" >/dev/null 2>&1 || true
  gh issue create --repo "$GH_REPO" --title "$title" --label "$ISSUE_LABEL" --body "$body"
fi
echo "drift reported: drifted=${drifted[*]:-none} failed=${failed[*]:-none}"
exit 2
