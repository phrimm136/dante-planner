---
status: Draft
tracking: none
---

# NNNN Title

## Summary

What is being built and why now, in two to four sentences, ending with one sentence on why this
leads the program at this point rather than later.

## Current behavior

What exists today, with the file and symbol names a reader can check. Brownfield only; delete this
section for greenfield work.

## Proposal

The design. Concrete enough that a reader can disagree with a specific part of it.

## Scenarios

Named, with literal values in every `then`. These become the child issues' contracts, so a scenario
transcribed vaguely becomes a test asserting the wrong thing.

```gherkin
Scenario: <name>
  Given <concrete precondition>
  When <the action>
  Then <literal expected value>
```

Use plain invariants instead for declarative state that has no actor — "the Workloads OU has four
policies attached" is an invariant, not a scenario, and forcing it into given/when/then obscures it.

## Invariants

Properties that must hold across the whole change, each with the gate that proves it.

## Decisions

Choices made here that meet the admission test in `../adr/README.md`. On acceptance these move into
an ADR; this section is the working draft, not the record.

- @tag @tag — <choice>, because <reason>. REJECTED: <alternative> — why it loses.

## Non-goals

The adjacent work this must not absorb, and any testable non-goal that earns a negative scenario.

## Risks and rollback

What breaks if this is wrong, how it is detected, and how it is undone.

## Open questions

Must be empty before the status advances past `Draft`.
