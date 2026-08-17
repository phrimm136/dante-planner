# Architecture Decision Records

A decision already made, with what lost and why. What belongs here rather than in an RFC, an issue
or a meme fact is settled in `../README.md`.

## Admission test

All three must hold, or it is not an ADR:

1. **Hard to reverse** — undoing it costs real rework.
2. **Unintuitive** — a future reader would plausibly re-litigate it, or silently un-make it.
3. **Genuine trade-off** — the rejected alternative had real merit.

A choice with no viable alternative is a constraint, not a decision. It belongs inside the bullet it
forced, as the reason that bullet reads the way it does.

## File

`NNN-kebab-title.md`. Three digits, next free number, never reused, gaps allowed. The title names
the **subject**, never the change that produced it.

## Grouping

One file per subject that shares a rate of change.

Not one file per task: it mixes volatile decisions with stable ones, so amending the volatile one
forces reopening settled neighbors. Not one file per decision: the directory becomes unreadable, and
supersession already addresses decisions more finely than a filename can.

The test when placing a decision: *would revising this later force me to touch settled work in the
same file?* If yes, it belongs somewhere else.

## Template

```markdown
# NNN kebab-title
epic: <id> · pr: <id> · supersedes: NNN @tag @tag

## Decisions
- @tag @tag [(taste)] — <decision, one sentence>. <the constraint that forced it>.
  REJECTED: <alternative> — <why it loses>.

## Superseded
- @tag @tag → NNN — <original text, verbatim>

## Takeaway
- takeaway: <the generalizable lesson>
```

`## Superseded` appears only once it has content. `supersedes:` appears only on a file that replaces
a decision elsewhere.

## The decision bullet

**Tags are the address.** A decision is cited as `NNN @tag @tag`, which survives regrouping in a way
a filename does not. Subject tag first, mechanism or concern after.

**`(taste)`** is the only sanctioned qualifier. It marks judgment without measurement, so a reader
knows how much weight the bullet carries. Nothing records how the decision was reached — who
proposed it, which session settled it, or whether anyone objected.

**`REJECTED:`** carries one clause per alternative, each with why it loses. It is required unless the
bullet is one of three cases:

- **forced** — the alternative is physically unavailable, so it was never rejected
- **corollary** — it follows from a sibling decision in the same file
- **summary** — it restates the file's decisions as a single contract

## Lifecycle

The record is **append-only**. A decision's text is never edited once written; it moves, and the
move is annotated.

| Event | Trigger | Handling |
|---|---|---|
| **Superseded** | a later decision contradicts it | Move the bullet verbatim into `## Superseded`, append `→ NNN`. The successor's metadata line gains `supersedes: NNN @tag @tag`. |
| **Fulfilled** | it named its own end condition, and that condition arrived | Same move, annotated `→ fulfilled`. |
| **Amended** | it stands; a detail changed | Edit in place. |

**Status is derived, never declared.** A file whose `## Decisions` section is empty is superseded by
construction. There is no status field to forget to update, and therefore none that can disagree
with the contents.

**An expiry clause is a task.** A decision scoped "while X holds" carries an action for whenever X
ends. Pair it with something mechanical that clears when the condition does, or it is untracked work
wearing a scope's clothes.

## Prohibitions

- No task or phase numbers, no dates, no session or conversation context, no attributions.
- **No paths to other documents.** A renamed, renumbered or superseded file leaves exactly the stale
  reference the rationale would have. Stable identifiers are exempt: an issue number survives a
  rename, a filesystem path does not, which is why the metadata line may point outward and the body
  may not.
- No account ids, ARNs, or trust topology. This repository is public.
- Source code never points back here. An ADR is found by searching this directory.

## Enforcement

`.githooks/pre-commit` checks the mechanical rules on staged ADRs: required sections, bullet shape,
filename and header agreement, banned tokens, and that every `supersedes:` resolves. `REJECTED`
coverage is reported, never enforced, because the three exceptions above are legitimate.

Enable it once per clone:

```bash
git config core.hooksPath .githooks
```
