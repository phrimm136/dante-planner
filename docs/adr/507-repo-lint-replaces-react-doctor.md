# 507 repo-lint-replaces-react-doctor
epic: none · pr: none

## Decisions
- @ci @frontend @lint — The react-doctor workflow is removed, and the checks that found real defects become the repo's own ast-grep rules, run by `check:fp` and tested by `check:rules` in PR Gate: `no-enter-without-composition-guard`, `no-state-seeded-from-prop`, `label-needs-control` and `input-needs-name`. A full react-doctor scan of the frontend returned 354 findings, of which 7 were real defects and none was caught by the repo's oxlint; every rule that produced only false or negligible findings was noise that each PR's reader had to re-verify. Re-check: `ls .github/workflows/react-doctor.yml` fails, and `yarn --cwd frontend check:fp` passes.
  REJECTED: keeping react-doctor with only the rules that found defects — it stays an advisory check outside PR Gate, adopts the repo's oxlint configuration (whose JS plugins need the frontend's installed dependencies), and still reports through PR comments only.
  REJECTED: keeping react-doctor with `adoptExistingLintConfig: false` — it fixes the failed lint pass but leaves 347 findings of noise for 7 defects.
  INVARIANT: an Enter-key handler checks IME composition, local state is seeded only from a prop named `initial*` or `default*` (or one the same component compares with `!==` to adjust state during render), every `<label>` names a control, and every `<input>` has a name — enforced by the four ast-grep rules in `check:fp`, which skip test files.
  NON-GOAL: checking the accessible names of the shadcn `Input` wrapper's callers — the rules see only the lowercase `<input>`, and the wrapper spreads its props.
  NON-GOAL: re-implementing react-doctor's performance, complexity, size or Fast Refresh rules — triage found no defect behind any of them.
- @frontend @lint — oxlint's `jsx-a11y/label-has-associated-control` and `control-has-associated-label` stay enabled, and the two ast-grep rules cover what they miss. oxlint 1.90 skips a `<label>` whose only content is an expression such as `{t('k')}`, and every label in this frontend is translated that way, so the oxlint rules never fire here. Re-check: a scratch file with `<label>{t('k')}</label><input />` produces no oxlint `label-has-associated-control` report.
  REJECTED: turning the oxlint rules off — they still cover literal-text labels in fixtures and future code, and cost nothing.
- @frontend @bundle — Detail-name loaders whose import path holds a per-id segment live in a module that the router reaches only through a dynamic import, and the entry-chunk budget is lowered to the size this gives. A template import such as `identity/${id}.json` compiles to one chunk pointer per file, and from a statically imported module those pointers land in the entry chunk: the four per-id loaders put about 3,800 of them there, roughly 63% of its gzip size. Re-check: a production build's entry chunk holds no `identity/`, `ego/`, `egoGift/` or `abEvent/` per-id JSON paths.
  REJECTED: loading names from the per-language name lists — the dungeon event list carries every event's full description, about 85 KB per language, for a 20-character title.
  INVARIANT: the entry chunk stays within `entryGzipBytes` — enforced by `check:bundle-budget` in PR Gate.

## Takeaway
- takeaway: a linter's finding count says nothing until each finding is checked against the code; the checks that survive belong in the gate that already blocks merges, not in a second advisory channel.
