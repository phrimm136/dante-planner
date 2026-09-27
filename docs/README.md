# docs

Four kinds of document, four jobs. Putting something in the wrong one is how it stops being read.

| Directory | Answers | Lifecycle |
|---|---|---|
| `adr/` | What did we decide, and what lost | Append-only; superseded by a successor, never rewritten |
| `runbooks/` | How do I execute this operation | Edited in place; retired when the operation no longer exists |
| *(root)* | How does the system work today | Edited in place; carries no history of its own |
| `legacy/` | What a retired process produced | Frozen; nothing here is maintained |

Execution tracking lives in GitHub issues, not here.

`debt.md` sits outside all four: a pruned parking lot of found work, written so a discovery stops
occupying attention, and read only by a deliberate defrag or design session. It is not a queue, and
nothing is expected to pull from it on a schedule.

## Choosing between them

An **ADR** is a decision already made: what was chosen, the constraint that forced it, the
alternatives and what killed each, and the consequences accepted. Admission is deliberately narrow
— see `adr/README.md`.

A **runbook** is a procedure someone follows under time pressure, usually with production at stake.
It is imperative, ordered, and carries its own gates and rollback. It is not a record of why.

An **issue** tracks execution: named scenarios and a position in a dependency order, transcribed
from an agreed design rather than being where the design happens.

## Standing reference

| File | Covers |
|---|---|
| `multi-region-request-paths.md` | How a request traverses the two regions |
| `prd.md` | Product requirements |
| `spec.md` | The data-driven feature template |
| `testing-principles.md` | The testing charter — tiers, what each owns, and the standing rules |
| `testing-evidence.md` | What has actually been verified, and how |
| `external-access.md` | Reaching production from a workstation |
| `Data Structure.md` | Static game-data shapes |

`testing-principles.md` states the policy and `testing-evidence.md` records the observations, so a
principle without a corresponding entry is an untested claim rather than a settled practice.

## Runbooks

| File | Operation |
|---|---|
| `runbooks/environment-setup.md` | Standing up a working environment from scratch |
| `runbooks/prod-account-rewire.md` | Preparing the vended prod account to receive production |
| `runbooks/rds-migration.md` | Managed-database migration and the decommission gate |
| `runbooks/redis-auth-password-rollout.md` | Shipping an auth Redis change without cutting its clients off |
| `runbooks/schema-decomposition-migration.md` | The stop-the-world schema window |
| `runbooks/staging-e2e-park-report.md` | Where the staging end-to-end loop was parked, and what resuming it needs |

## Legacy

`legacy/` holds a retired process: task directories carrying spec, plan, research and review
documents, and the decision index generated from them. Decisions worth keeping were harvested into
`adr/`, live procedures were promoted into `runbooks/`, and the rest stays only for the record git
already keeps. It also takes runbooks whose operation no longer exists, under `legacy/runbooks/`.
Nothing there is maintained, and nothing outside it should point into it.
