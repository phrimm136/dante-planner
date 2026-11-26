# 573 effectful-method-branch-counts-are-a-measurement
epic: none · pr: none · supersedes: 553 @shell

## Decisions
- @shell @archunit @measurement — The branches of every main method that reaches an effect (by the architecture tests' effect walk, logging ignored) are counted and kept in a committed, sorted list that always matches the source; the list never blocks a change, and a line that rises in a diff is where review asks whether a decision sits inside an effect method. Kept as a gate that refused a raise without a reason, the count became the target: code wrapped a boolean or a null into a type on the switch line and wrote `case null` for `if (x == null)`, and once early returns went uncounted a five-fact decision sat in two of them. Re-check [manual]: `checks be test --tests 'org.danteplanner.backend.architecture.ShellBranchTest' --exclude-tags containerized`.
  REJECTED: keeping the gate and closing each evasion in the counter — every closing moved the code to the next form, and the forms are listed as review questions instead.
  REJECTED: dropping the count — without it a review has no pointer to the methods whose branching grew.
  REJECTED: counting branches in bytecode — javac lays out loops, guards and switches as jumps, so the count needed guesses about its layout and missed lambda bodies, constructors and the last `if` of a loop; the call graph decides what is effectful and the source decides what is a branch.
  INVARIANT: the list matches the counted source — `ShellBranchTest`; `checks be freeze-branches` rewrites it and prints every count that moved, with no reason asked and nothing refused.
  INVARIANT: a guard, an `if` without `else` whose body is one `throw` with the `&&` and `||` of its condition, is uncounted, because a throw ends the caller's work too and two of them cannot split an if/else; a bare `return;` or `continue` guard, a value return, a two-statement body, a `case null` and a `default` are counted — the fixture cases of `ShellBranchTest`.
  NON-GOAL: code with no source (Lombok), field and static initializers, and a `switch` whose labels are named constants only — uncounted; the effect walk attributes an anonymous class to its enclosing method.
  NON-GOAL: judging a method by its number — a raised line is a place to read, and a finding still quotes the shape question it fails.

## Takeaway
- takeaway: a count that gates a change becomes what the change optimizes; keep it accurate and visible, and let the reader judge what it points at.
