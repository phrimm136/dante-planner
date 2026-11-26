# 519 the-review-gate-sits-on-the-push
epic: none · pr: none

## Decisions
- @harness @landing — A push of a branch is refused by the git `pre-push` hook unless every non-merge commit that is not on `origin/main` has its patch-id listed in a review record under the main checkout's reports directory. A branch deletion, a tag and an empty commit pass. This supersedes the `reference-transaction` guard of ADR 137: that hook guarded the ref of a local integration branch, and work now reaches the trunk as a pull request, so no local ref moves when a unit lands and the guard saw nothing. The push is the last local step every unit takes, whichever worktree it leaves from. The patch-id key, the reviewer-written record, the delta entry for fix commits stay as ADR 137 decided. The human bypass is the hooks path unset for one command, or `git push --no-verify`, which skips `pre-push` and which the old hook did not have; the Bash-tool hook denies both forms to the agent. Re-check [manual]: `.githooks/tests/pre-push.sh` passes, and `rg -q 'pre-push' .githooks/tests/pre-push.sh`.
  REJECTED: retiring the gate and keeping the review as a written rule — a rule without a gate is skipped: the pull requests opened after the old hook went blind carried no review record.
  REJECTED: a required status check on the pull request — the review records are untracked and live on one machine, so CI cannot read them.
  REJECTED: retargeting `reference-transaction` to the local trunk ref — the local trunk is a read-only mirror that moves only by fetch, and every such move is a sync the hook exempts.
  INVARIANT: no commit leaves the machine on a branch without a review record of its patch-id — enforced by `.githooks/pre-push`, tested by `.githooks/tests/pre-push.sh`.
  NON-GOAL: checking what a review record says — the hook checks that the record names the commit, never the quality of the review (ADR 137, @harness @hooks).

## Takeaway
- takeaway: a gate belongs on the step every change still takes; when the workflow moves, a gate on the old step fails open and silently.
