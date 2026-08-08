# Review calibration

Practices for consuming reviewer findings in this repo.

- **Validate findings before applying.** Reviewers can be confidently wrong about
  project conventions or runtime contracts. For each finding: confirm it at the cited
  file:line, check the proposed fix actually improves correctness, and verify
  config/API claims via dry-run, tests, or grep. State a verdict per finding
  (Fix / Skip — reason / Confirmation); never silently drop one.
- **Behavior-preserving refactors: diff findings against the original.** For a
  rename/move/split, a "this changed behavior" or "this introduced disorder" finding is
  only real if it differs from the pre-change source. Use `git show HEAD:<path>` on the
  moved or deleted original (or the consumer's prior import block) to classify each
  finding as regression-vs-preserved. A faithful move that carries a pre-existing wart
  forward did not introduce it; scope discipline means leaving it.
