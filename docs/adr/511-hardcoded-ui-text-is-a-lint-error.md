# 511 hardcoded-ui-text-is-a-lint-error
epic: none · pr: none

## Decisions
- @lint @ui-text — A local oxlint JS plugin rule, `ui-text/no-hardcoded-text`, reports text that reaches a user without `t()`. It covers JSX text and literals that reach a JSX child or a user-visible attribute, also through `?:`, `&&` and `||`. It also covers object properties named `label`, `title`, `description` and `placeholder`, and string defaults of props whose names mark visible text. Strings with no letter, i18n-key shapes, URLs, class lists and the language names in the language switcher are exempt. A scanner that someone has to remember to run does not stop new text from shipping. Re-check: `checks fe test src/__tests__/uiTextLint.test.ts` passes, and its violator fixture reports `<button>Close</button>`.
  REJECTED: the native `react/jsx-no-literals` rule — it sees only JSX. Router head titles, option label arrays and default props stay unchecked.
  REJECTED: a literal port of the old scanner — it flagged every string literal with a space or a capital, so error messages, font licences and SVG paths failed beside the user-visible text, and every new error message failed the lint.
  NON-GOAL: text that reaches a user through a variable, a prop, an array mapped into JSX or another module — it still ships unflagged. The rule is syntactic by choice: it judges a literal by where it stands, and following values across statements and files needs data-flow analysis that the rule does not do.
- @lint @ui-text @baseline — Existing hits live in a baseline file keyed by file and then by exact normalized text, with a count per text. A hit that the baseline does not name fails the lint. A baseline entry that occurs fewer times than its count also fails, and the message tells the reader to lower or remove it. No tool adds entries, so the baseline can only shrink. A vitest guard fails on a key that names a missing file, because a lint rule never sees a deleted file.
  REJECTED: oxlint's native suppressions file — it counts per file and rule. A new hit can hide behind a fixed one in the same file, which is the shape rejected for compiler bailouts.
  REJECTED: a base-branch diff step in CI — every added entry is an exact text in the diff, so review sees it.
  INVARIANT: every hit is in the baseline, and every baseline count equals the current count — enforced by the rule and the orphan guard test.
  NON-GOAL: translating the baselined hits — later work moves that text into the i18n files of the data repository.

## Takeaway
- takeaway: a ratchet keyed by count lets one change hide another. Key it by the thing a reviewer names.
