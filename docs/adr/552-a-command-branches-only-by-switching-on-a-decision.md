# 552 a-command-branches-only-by-switching-on-a-decision
epic: none · pr: none

## Decisions
- @command @archunit @astgrep — A method that performs effects and reports what it did returns a type that implements the `CommandOutcome` marker and whose name ends in `Outcome`, and it branches only by an exhaustive `switch` over a decision value or by a guard (553): no other `if`, no ternary. A command reads the facts its decision needs, stopping early on one that settles the case, and one pure decision runs on all of them (the access-token check reads the revocation and the current login, then decides once). Notification dispatch, refresh rotation, the access-token check and publication returned an outcome while deciding it with `if` between their writes.
  REJECTED: a marker alone — a reader cannot see from a signature which methods may act; the `Outcome` name makes the source-level rule possible, since ast-grep sees names, not types.
  REJECTED: a name alone — a type named `...Outcome` by accident would silently become a command; the pairing rule fails on either half missing.
  REJECTED: banning `switch` too — a command has to dispatch on the decision it received, and the exhaustive switch is that dispatch.
  INVARIANT: marker and name agree — a type implementing the marker directly ends in `Outcome`, and a sealed type or enum ending in `Outcome` implements the marker (the pairing test in `DecisionPurityTest`).
  INVARIANT: an `Outcome`-returning method in main contains no `if` but guards and no ternary, lambdas included — the `no-branch-in-outcome-command` ast-grep rule and its test cases.
  NON-GOAL: try/catch — turning an exception into a value at the boundary stays allowed.
  NON-GOAL: test sources — a test fake that returns an outcome may branch.

## Takeaway
- takeaway: when a rule must be checked by two tools that see different things, give the thing both a type and a name, and make a third check keep them paired.
