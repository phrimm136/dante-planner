# 078 published-upsert-restriction-gate
epic: none · pr: none

## Decisions

- @planner @moderation — Restriction gates on the planner's public state, not the endpoint's
  classification: an upsert of a published planner rejects a restricted user before sync-version
  validation (403 over 409) and rejects the write in its entirety, while private planner work
  stays available under both a timeout and a ban. The forcing constraint: publicness is a
  property of the aggregate's state, and the sync endpoint carries a conditional public side
  effect (catalog propagation) that endpoint-level enforcement never covered, leaving the check
  only in the bypassable client. REJECTED: blocking all sync for restricted users — withdraws
  the private work the guard's contract preserves. REJECTED: status quo — the one public
  mutation without a server-side check.

## Takeaway

- takeaway: enforcement attached to endpoint classification fails exactly where an endpoint's
  publicness is data-dependent; the check must follow the state, not the route.
