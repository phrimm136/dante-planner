# 081 coordinator-service-shape
epic: none · pr: none

## Decisions

- @query (taste) — PublishedPlannerQueryService stays whole. Its method groups use partially
  disjoint dependencies, but three infrastructure dependencies span the groups, so a list/detail
  split yields two aggregators sharing state for the price of a new seam. REJECTED: list/detail
  split — the cohesion gain does not cover the seam cost.
- @publishing @purge @lifecycle (taste) — The publishing, account-purge, and account-lifecycle
  coordinators are accepted at their current dependency counts: each dependency set is one
  workflow's named collaborators, so the counts are honest rather than symptomatic. REJECTED:
  command-handler decomposition — relocates knowledge without removing it.

## Takeaway

- takeaway: a dependency count is a symptom ledger, not a verdict; a homogeneous, single-workflow
  dependency set reads high and costs nothing, and splitting it buys a seam, not cohesion.
