# 553 an-effectful-method-branches-only-by-switch
epic: none · pr: none

## Decisions
- @shell @archunit @ratchet — A main method that reaches an effect (by the architecture tests' allowlist effect walk, logging ignored) contains no conditional branch beyond guards: it is a shell that branches only by `switch` over a decision value, and the decision itself is computed by a pure method. A guard is an `if` without `else` whose body is one bare `return;`, one `throw` or one `continue`, the `&&` and `||` of its condition included; it ends the work and chooses no other effect. A `switch` with a `case null` or a `default` label is a branch. Counting guards made code wrap a boolean or a null into a type on the line that switched on it: the restriction hook grew from 172 to 328 lines that way, and 27 listed methods had nothing but guards. A decision written as an `if` inside a method that also waits, writes or publishes is how effects kept entering decision code: the freshness guard's `decide` waited, recorded a timer and marked a source skipped while choosing a verdict, and about 290 effectful methods branched when the rule was added. Re-check: `checks be test --tests 'org.danteplanner.backend.architecture.ShellBranchTest' --exclude-tags containerized`.
  REJECTED: a rule over methods that return a decision type only — it covered about nine of those methods; the rest return void, a boolean or an entity, where the mixing happens unseen.
  REJECTED: counting branches in bytecode — javac lays out loops, guards and switches as jumps, so the count needed guesses about its layout and missed lambda bodies, constructors and the last `if` of a loop; the call graph decides what is effectful and the source decides what is a branch.
  REJECTED: a source rule alone (ast-grep over `if`) — source cannot tell an effectful method from a pure one.
  REJECTED: banning loops in shells — a loop that applies an effect to each item decides nothing; the conditions inside it are counted.
  REJECTED: a guard that returns a value — a run of them is a decision table inside an effect method.
  REJECTED: leaving `case null` and `default` uncounted — a `switch (x) { case null -> ... }` is an `if (x == null)` the count did not see, and rewrites used it to pass.
  INVARIANT: a guard is uncounted and a value return, a two-statement body, a `case null` and a `default` are counted — the fixture cases of `ShellBranchTest`.
  INVARIANT: no effectful method gains a branch — a committed, sorted list holds each such method with its branch count; a method above its line, or with no line, fails, and a line above its method, or of a method that is gone, fails until it is rewritten (`ShellBranchTest`).
  INVARIANT: the regenerating command refuses to add or raise a line without a stated reason (`ShellBranchTest`, the refusal case), and an accepted run prints every line it raised (`ShellBranchTest`, the case given a reason). The reason is not stored, and a line raised by hand passes: the diff of the list is where a raise is reviewed.
  NON-GOAL: fixing the frozen methods — each shrinks in the unit that touches it.
  NON-GOAL: code with no source (Lombok), field and static initializers, and a `switch` whose labels are named constants only — uncounted; the effect walk attributes an anonymous class to its enclosing method.

## Takeaway
- takeaway: a rule meant to stop a pattern from spreading has to look where the pattern lives, not where it was first noticed; a narrow rule reports compliance for the code it never looked at.
