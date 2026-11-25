# 568 a-request-states-the-state-it-wants
epic: none · pr: none

## Decisions
- @api @idempotent @subscription — A request that changes a two-state setting names the state it wants (`PUT` and `DELETE /api/planner/md/{id}/subscription`, or a body with `enabled`), and the statement assigns that value. A request that says "the other one" is undone by a retry, a second tab or a second click sent before the first answer returned, and making the flip one atomic statement removes the race between two flips but not the flip.
  REJECTED: disabling the button while a request is in flight — it covers one tab and no retry.
  REJECTED: an idempotency key on the flip — storage and expiry for a problem the request shape removes.
  INVARIANT: no `@Query` assigns a column from its own negation except the lines of `backend/config/self-flip-queries.txt`, which only shrink — `SelfFlipQueryTest`.
  NON-GOAL: an increment, which is not a setting — `count = count + 1` is not matched.
- @api @rollout — `POST /api/planner/md/{id}/subscribe` stays beside the new routes until the frontend no longer calls it: the frontend and the backend deploy separately, and an open tab keeps the old script. Removal condition: the frontend release that uses the new routes is live, and the old route then has no call for 24 h in `http_server_requests_seconds_count`.
  REJECTED: removing it with the new routes — the subscribe button fails in every open tab.

## Takeaway
- takeaway: atomicity makes a flip correct once; only naming the target makes it correct twice.
