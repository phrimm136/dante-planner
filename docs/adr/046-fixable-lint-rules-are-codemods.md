# 046 fixable-lint-rules-are-codemods
epic: none · pr: none

## Decisions
- @lint @hooks — A fixable lint rule is adopted as a codemod, not a diagnostic: the post-edit hook runs the linter's `--fix` on every edited file, so enabling a fixable rule at any severity schedules its rewrite for every file anyone touches. Warning severity governs only whether CI blocks; it does not make a fixable rule advisory. REJECTED: judging rules by violation count and severity alone — two rules admitted that way rewrote code semantically before their first CI failure.
- @lint @types — `typescript/consistent-type-definitions` stays off. Its fix rewrites `interface` to `type`, which breaks `declare module` augmentations (interfaces merge across declarations, type aliases collide — the router's `Register` registration), and the codebase's house convention is `interface` at hundreds of sites. REJECTED: enabling it at warn to steer new code — the hook applies the rewrite on touch regardless of severity.
- @lint @es — `unicorn/no-array-sort` stays off while the TypeScript `lib` target is ES2022: its fix rewrites `.sort()` to `.toSorted()`, which that lib does not declare, so any touched file containing a sort stops typechecking. Reconsider if `lib` is raised to ES2023 or later. REJECTED: keeping it at warn for the mutation-hygiene signal — same rewrite-on-touch behavior.

## Takeaway
- takeaway: when a hook auto-applies fixes, the adoption question for a fixable rule is not "is the diagnostic right" but "is the rewrite semantically safe on every construct it matches" — and declaration merging and lib-versioned APIs are where mechanically-plausible rewrites are wrong.
