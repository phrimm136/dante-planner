#!/usr/bin/env bash
# Nightly terraform drift detection across all stacks (stage 1: detect only).
#
# All stacks keep LOCAL state + gitignored tfvars, so this must run where the
# state lives (a workstation systemd --user timer), not in GitHub Actions.
# Reports drift as a GitHub issue carrying ONLY stack names and change counts —
# the repo is public, so plan bodies (resource ARNs, topology) never leave the
# machine; full logs stay under /tmp for the operator.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
GH_REPO="phrimm136/dante-planner"
ISSUE_LABEL="terraform-drift"
STACKS=(iam-bootstrap oregon oregon-edge seoul rds secrets global-accelerator)

drifted=()
failed=()
summary=""

for stack in "${STACKS[@]}"; do
  dir="$REPO_DIR/terraform/$stack"
  [ -d "$dir" ] || continue
  log="/tmp/tf-drift-$stack-$(date +%Y%m%d).log"
  terraform -chdir="$dir" plan -detailed-exitcode -input=false -lock=false -no-color \
    >"$log" 2>&1
  rc=$?
  case "$rc" in
    0) ;;
    2)
      drifted+=("$stack")
      counts=$(grep -Eo 'Plan: [0-9]+ to add, [0-9]+ to change, [0-9]+ to destroy' "$log" | tail -1)
      summary+="- \`$stack\`: ${counts:-diff present} (log: $log)"$'\n'
      ;;
    *)
      failed+=("$stack")
      summary+="- \`$stack\`: plan FAILED rc=$rc (log: $log)"$'\n'
      ;;
  esac
done

if [ "${#drifted[@]}" -eq 0 ] && [ "${#failed[@]}" -eq 0 ]; then
  echo "no drift across ${#STACKS[@]} stacks"
  exit 0
fi

title="terraform drift: ${drifted[*]:-} ${failed[*]:+(plan failures: ${failed[*]})}"
body="Nightly drift check on $(hostname) at $(date -Iseconds).

$summary
Counts only — full plan output stays on the operator machine (public repo)."

existing=$(gh issue list --repo "$GH_REPO" --label "$ISSUE_LABEL" --state open \
  --json number --jq '.[0].number' 2>/dev/null)
if [ -n "$existing" ] && [ "$existing" != "null" ]; then
  gh issue comment "$existing" --repo "$GH_REPO" --body "$body"
else
  gh issue create --repo "$GH_REPO" --title "$title" --label "$ISSUE_LABEL" --body "$body" 2>/dev/null \
    || gh issue create --repo "$GH_REPO" --title "$title" --body "$body"
fi
echo "drift reported: drifted=${drifted[*]:-none} failed=${failed[*]:-none}"
exit 2
