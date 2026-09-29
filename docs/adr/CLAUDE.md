# Writing an ADR

Full convention with rationale: `README.md` in this directory.

## Before writing one

All three must hold, or it does not belong here:

1. Hard to reverse — undoing it costs real rework.
2. Unintuitive — a future reader would re-litigate it or silently un-make it.
3. Genuine trade-off — the rejected alternative had real merit.

A choice with no viable alternative is a constraint. Fold it into the bullet it forced.

## Placement

One file per subject sharing a rate of change. Never one file per task. Before adding a decision to
an existing file, ask whether revising it later would force touching settled work beside it.

## Shape

```markdown
# NNN kebab-title
epic: <id> · pr: <id> · supersedes: NNN @tag @tag

## Decisions
- @tag @tag [(taste)] — <decision, one sentence>. <the constraint that forced it>.
  REJECTED: <alternative> — <why it loses>.
  INVARIANT: <property that must always hold> — <its enforcer: type, test, constraint, or "unenforced">.
  NON-GOAL: <what it deliberately does not do> — <why>.

## Superseded
- @tag @tag → NNN — <original text, verbatim>

## Takeaway
- takeaway: <the generalizable lesson>
```

`NNN` is the next free number, three digits, never reused. Every decision line starts `- @`.
`## Superseded` and `supersedes:` appear only when they have content.

`INVARIANT:` and `NON-GOAL:` lines are the decision's consequences; write every invariant and
non-goal the decision settles, in the bullet it follows from.

`(taste)` is the only qualifier — it marks judgment without measurement. `REJECTED:` is required
unless the bullet is forced (no alternative existed), a corollary of a sibling, or a summary of the
file's other decisions.

## Never write

- Task or phase numbers, dates, session or conversation context, attributions of who decided.
- A path to another document. Stable identifiers (issue, PR) are fine on the metadata line; a
  filesystem path in the body is not.
- Account ids, ARNs, or trust topology. This repository is public.
- A pointer to an ADR from source code, comments, or commit messages.

## Editing an existing ADR

The record is append-only. Never rewrite a decision's text.

- A later decision contradicts it → move the bullet verbatim into `## Superseded`, append `→ NNN`,
  and add `supersedes:` to the successor's metadata line.
- Its stated end condition arrived → same move, annotated `→ fulfilled`.
- A detail changed but the decision stands → edit in place.

Status is derived from structure: an empty `## Decisions` section means the file is superseded.

## Check before committing

```bash
.githooks/pre-commit
```
