---
status: Draft
tracking: none
---

# NNNN Title

## Summary

What is being built, in two to four sentences.

## Motivation

Why do anything at all — the problem, who has it, and what is true after this lands. Distinct from
the rationale for *this particular shape*, which belongs in Decisions. Collapsing the two lets a
proposal justify its design by restating the problem.

## Current behavior

What exists today, with the file and symbol names a reader can check. Brownfield only; delete this
section for greenfield work. These facts are a snapshot: the status field dates them, so they are
allowed to go stale once this reads `Implemented`.

## Prior art

Who else has solved this, and what happened to them. Other projects, other languages, published
guidance, and any earlier attempt inside this repository. The point is to be judged fairly against
what already exists — and a search that kills the proposal here is far cheaper than one that kills
it during implementation.

## Proposal

The design. Concrete enough that a reader can disagree with a specific part of it.

State properties, not locations. A structural choice that was argued belongs in Decisions with its
rejected alternative; a structural choice nobody argued belongs in the code, because a filename
written here becomes a second copy that drifts.

## Decomposition

The units of work and the dependency edges between them — a DAG, not a schedule. `/implement-rfc`
builds its graph from this section, and `/open-tracking-issue` mirrors it as the tracking issue's
checklist.

```
- <node-name> — <the outcome, one line>
- <node-name> — <the outcome> (after: <node-name>)
```

Every scenario below is assigned to exactly one node, and every node owns at least one scenario.
Each dependency edge needs a consumer-side scenario on the downstream node pinning exactly what it
reads.

## Scenarios

Named, with literal values in every `then`. These are the implementation contract, so a scenario
transcribed vaguely becomes a test asserting the wrong thing — "works correctly" is an intent gap,
not a specification.

```gherkin
Scenario: <name>
  Given <concrete precondition>
  When <the action>
  Then <literal expected value>
```

Write them declaratively: state the behavior, not the mechanics of reaching it. A scenario that
names UI controls or HTTP payload fields is an automation script in English and breaks on every
implementation change.

Use an invariant instead for declarative state with no actor — "the Workloads OU has four policies
attached" is an invariant, and forcing it into given/when/then obscures it.

Scenarios may be amended after acceptance, by commit, when a mid-flight ruling covers behavior
nothing here specified.

## Invariants

Properties that must hold across the whole change, each named with the gate that proves it. An
invariant with no gate is a comment.

## Decisions

Choices made here that meet the admission test in `../adr/README.md` — hard to reverse, unintuitive,
and a genuine trade-off. On completion these migrate into an ADR; this section is the working draft,
not the record, and it may still change while the status is `Draft`.

- @tag @tag — <choice>, because <reason>. REJECTED: <alternative> — why it loses.

## Drawbacks

Why should we *not* do this. Specifically: what does this proposal make impossible or expensive
later? A drawback is not the same as a risk — a risk is something that might go wrong, a drawback is
a cost paid even when everything goes right.

## Non-goals

The adjacent work this must not absorb, and any testable non-goal that earns a negative scenario.

## Risks and rollback

What breaks if this is wrong, how it is detected, and how it is undone. Name anything that becomes
permanently unundoable once this ships.

## Open questions

Must be empty before the status advances past `Draft`.
