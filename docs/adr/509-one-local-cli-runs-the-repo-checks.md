# 509 one-local-cli-runs-the-repo-checks
epic: none · pr: none

## Decisions
- @tooling @checks — Every repeated local check of the frontend, e2e and backend runs through one CLI, `checks` (`checks <lane> <check>`), which takes the shared test lock, writes a dated log and prints one PASS/FAIL line with the tool's own summary; the Bash hook refuses the hand-assembled equivalents (direct vitest, tsc, oxlint, oxfmt, playwright, gradlew, or the matching yarn scripts) and names the `checks` command instead. The hook used to police command shape with regexes (a `--cwd` target, a literal log path, a PASS/FAIL suffix), and a shape check cannot be complete: `yarn --cwd <dir> run typecheck` matched none of its patterns and skipped every rule, while correctly shaped commands kept tripping it. Re-check: the hook refuses `yarn --cwd frontend run typecheck` and passes `checks fe typecheck`.
  REJECTED: tightening the regexes — each new tool, flag or spelling needs another pattern, and the gap is found only after a command slips through.
  REJECTED: having CI call `checks` — local and CI stay separately defined; `checks fe gate` reads the frontend steps from the PR Gate workflow instead, so the local gate follows CI without a second list.
  INVARIANT: a check that runs locally holds the test lock and leaves a log — enforced by the hook, which admits only `checks` for those tools.
- @tooling — Command-line tools are split by kind: `checks` only runs checks and changes nothing outside the working tree, operations that reach production or cloud state get their own CLI, and the static-checkout and debt-ledger CLIs stay separate. A permission rule that allows a checks CLI must never also allow a production command. Re-check: `checks list` names no command that reaches the network beyond package installs.
  REJECTED: one CLI with an operations namespace — a single allow rule would cover both, and the boundary would rest on each subcommand remembering to ask.
  NON-GOAL: the operations CLI itself — it is a separate unit.

## Takeaway
- takeaway: a hook that guesses which commands are checks will always miss a spelling; give the checks one entry point and let the hook admit only that.
