# 553 an-effectful-method-branches-only-by-switch
epic: none · pr: none

## Decisions
- @shell @archunit @ratchet — A main method that reaches an effect (by the architecture tests' allowlist effect walk, logging ignored) contains no forward conditional jump in its own bytecode: it is a shell that branches only by `switch` over a decision value, and the decision itself is computed by a pure method. A decision written as an `if` inside a method that also waits, writes or publishes is how effects kept entering decision code: the freshness guard's `decide` waited, recorded a timer and marked a source skipped while choosing a verdict, and about 290 effectful methods branched when the rule was added. Re-check: `checks be test --tests 'org.danteplanner.backend.architecture.ShellBranchTest' --exclude-tags containerized`.
  REJECTED: a rule over methods that return a decision type only — it covered about nine of those methods; the rest return void, a boolean or an entity, where the mixing happens unseen.
  REJECTED: a source-level rule (ast-grep over `if`) — source cannot tell an effectful method from a pure one, which needs the call graph across classes and interfaces.
  REJECTED: banning loops in shells — a stream `forEach` is a lambda and already escapes the rule, so banning `for` and `while` alone would make the same shell legal in one spelling and illegal in the other.
  INVARIANT: no new effectful method branches — the existing violations are frozen with ArchUnit's FreezingArchRule in a store kept outside the test classpath; the ArchUnit configuration forbids creating or updating the store during a run, so a new violation fails, and a stored line that no longer violates fails until it is deleted (`ShellBranchTest`, the late-violation and stale-line tests).
  INVARIANT: the store belongs to the rule as worded — rewording the rule changes its store key and would freeze every violation afresh, so a test requires exactly one key, equal to the current description, and no orphan file.
  NON-GOAL: counting branches inside a frozen method — the store names methods, so a frozen method can gain branches unseen, and a changed signature needs its line replaced by hand.
  NON-GOAL: fixing the frozen methods — each shrinks in the unit that touches it.
  NON-GOAL: branches the bytecode view cannot see or attributes elsewhere — lambda bodies (synthetic methods are not roots), constructors, an `if` whose jump lands on the loop head (the last statement of a loop body), `if (c) break;` and the extra terms of a loop condition, which jump to the loop exit. The shapes are javac's, pinned by fixtures; a switch over a String counts as a branch, because it is not a switch over a decision value.

## Takeaway
- takeaway: a rule meant to stop a pattern from spreading has to look where the pattern lives, not where it was first noticed; a narrow rule reports compliance for the code it never looked at.
