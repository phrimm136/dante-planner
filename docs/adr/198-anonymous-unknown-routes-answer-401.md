# 198 anonymous-unknown-routes-answer-401
epic: none · pr: none

## Decisions
- @security @problem-body — An anonymous request to a route nothing serves answers 401 with a
  problem body, not 404. `SecurityConfig` closes with `anyRequest().authenticated()`,
  authorization runs before dispatch, and route existence is not disclosed to callers who have
  not authenticated; the 404 problem body of an unmatched route is observed on an
  authenticated request. Re-check: an anonymous `GET /api/typo` against any running instance
  answers `401` with `code: UNAUTHORIZED`. The contract spec asserted 404 for the anonymous
  probe from its first version and never passed on trunk; the spec, not the filter chain,
  carried the mistake.
  REJECTED: a filter ahead of the security chain that consults the handler mappings and
  answers 404 for unmatched paths — discloses which routes exist to anonymous callers and adds
  a filter whose only job is to contradict the chain's order.
  REJECTED: a `permitAll()` matcher for unmatched paths — `anyRequest()` must be last, so a
  "no known route" matcher is a maintained list of every route.
  INVARIANT: an anonymous `GET /api/<unmatched>` answers 401 with a body `ProblemSchema`
  parses and `code: 'UNAUTHORIZED'` — the contract spec "an anonymous unknown route answers
  401 with a problem body".
  NON-GOAL: answering 404 before authentication for any route — it would disclose the route
  table to anonymous callers.

## Takeaway
- takeaway: a contract spec that has never been green on trunk is a claim, not a contract;
  the first question is which side the claim describes.
