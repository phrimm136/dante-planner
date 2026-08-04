#!/usr/bin/env bash
# Nightly terraform drift detection (stage 1: detect only).
#
# Covers only stacks whose state lives in S3. terraform/iam-bootstrap and
# terraform/state-backend keep local state because they create the bucket, so a runner that
# never held that file would plan a full rebuild and report it as drift. Check those two by
# hand where the state lives.
#
# Reports ONLY stack names and change counts — the repo is public, so plan bodies (resource
# ARNs, topology) never reach the issue. Plan logs stay on the machine that ran the check and
# are never uploaded; reproduce locally to see why a stack failed.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
GH_REPO="phrimm136/dante-planner"
ISSUE_LABEL="terraform-drift"
STACKS=(oregon seoul rds secrets global-accelerator)

# Stacks whose inputs are not in an auto-loaded terraform.tfvars. Anything absent here relies on
# the auto-load, which composes with -var-file rather than being replaced by it.
declare -A VAR_FILE=( [rds]=prod.tfvars )

BACKEND_CONFIG="${BACKEND_CONFIG:-$REPO_DIR/terraform/backend.hcl}"

drifted=()
failed=()
summary=""

for stack in "${STACKS[@]}"; do
  dir="$REPO_DIR/terraform/$stack"
  [ -d "$dir" ] || continue
  log="/tmp/tf-drift-$stack-$(date +%Y%m%d).log"

  args=(-detailed-exitcode -input=false -lock=false -no-color)
  [ -n "${VAR_FILE[$stack]:-}" ] && args+=(-var-file="${VAR_FILE[$stack]}")

  if [ ! -d "$dir/.terraform" ]; then
    terraform -chdir="$dir" init -input=false -no-color \
      -backend-config="$BACKEND_CONFIG" >"$log" 2>&1 || {
      failed+=("$stack")
      summary+="- \`$stack\`: init FAILED (log: $log)"$'\n'
      continue
    }
  fi

  terraform -chdir="$dir" plan "${args[@]}" >"$log" 2>&1
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
\`terraform -chdir=terraform/<stack> plan\`"

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
