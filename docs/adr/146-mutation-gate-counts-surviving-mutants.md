# 146 mutation-gate-counts-surviving-mutants
epic: none · pr: none · supersedes: 137 @harness @gates

## Decisions
- @harness @gates @mutation — The mutation gate fails when any measured package has more surviving
  mutants than its committed count, and asks for the count to be lowered when a package has fewer;
  a package absent from the counts has zero. It gates the number of changes no test notices, which
  only new untested code can raise; a score ratio also fell when well-tested code was deleted,
  and a validation refactor that removed its killed parsers failed the gate with no test weaker.
  REJECTED: the up-only score threshold — deleting well-tested code lowers it, so a correct
  simplification had to add unrelated tests or lower a threshold that is meant never to fall.
  REJECTED: gating only the mutants on changed lines — needs history or SCM integration the build
  does not have, and leaves no committed baseline to read.
  INVARIANT: no measured package's surviving-mutant count rises — the mutation ratchet task
  against the committed survivor counts; a change that removes one survivor and adds another in
  the same package keeps the count and passes.
  NON-GOAL: the measured scope stays the listed packages; widening it is a separate choice.
- @harness @gates — Every build-side check (shared case corpus, derived-subject invariant tests,
  boundary lints, contract scripts) joins the per-unit local gates, since the pull-request
  workflow runs only against the release branch and would see a unit at range end, which is the
  latency being removed. Corollary of the landing decision in 137.

## Takeaway
- takeaway: a ratio gate punishes deleting what it measured well; gate the count of what no test
  catches.
