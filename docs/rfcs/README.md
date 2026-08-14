# RFCs

A proposal under review. What belongs here rather than in an ADR, a runbook or an issue is settled
in `../README.md`.

## Index

| # | Title | Status | Tracking |
|---|---|---|---|
| [0001](0001-aws-account-topology.md) | AWS account topology | Accepted | none |
| [0002](0002-backend-failure-and-effect-conventions.md) | Backend failure, effect, and enforcement conventions | Accepted | [243](https://github.com/phrimm136/dante-planner/issues/243) |
| [0003](0003-sync-identity-and-effect-delivery.md) | Sync identity and effect delivery | Accepted | none |
| [0004](0004-frontend-sync-storage-and-consolidation.md) | Frontend sync storage and consolidation | Accepted | none |

## Lifecycle

| Status | Meaning |
|---|---|
| `Draft` | On the default branch, readable and citable, not yet agreed |
| `Accepted` | Agreed; issues may be cut from it |
| `Implemented` | Every decomposition node landed and the tracking issue closed |
| `Rejected` | Argued and declined; kept, because the argument is the value |
| `Superseded by NNNN` | Replaced by a later proposal |

**Merging is not acceptance.** An RFC lands on the default branch early, in `Draft`, and advances by
editing its status field. Two reasons: the argument then has a diff rather than a comment thread,
and the detached worktree threads that implement it read from the default branch, so a proposal
sitting on a feature branch is invisible to the machinery that consumes it.

**Open questions gate the status.** An RFC with anything under Open Questions stays `Draft`.

## Numbering

`NNNN-kebab-title.md`, four digits, next free number, never reused. `0000-template.md` is the
template and is not an RFC.

## Flow

```
/argue      adversarial design debate, ending in a handoff
/rfc        transcribe the handoff here, open the PR, merge in Draft
            (status -> Accepted once open questions are closed)
/create-issue   epic linking here + dependency-ordered children carrying the scenarios
/implement-issue  worktree threads read the RFC from disk
/reconcile      implementation audited against this document
/wrap-up        status -> Implemented; decisions that earn it move to ../adr/
```

## Relationship to ADRs

An RFC argues; an ADR records. A decision inside an RFC is a working draft — it may still change
while the status is `Draft`. On `Implemented`, decisions meeting the admission test in
`../adr/README.md` move into an ADR, where they become append-only. The RFC is not the durable home
for a decision, because a proposal's reasoning is allowed to be wrong and a record's is not.
