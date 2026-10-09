# 177 the-consistency-checker-reads-one-line-per-operation
epic: none · pr: none @consistency-checker @history-format

## Decisions
- @consistency-checker @history-format — A history is a JSON Lines file with one line per operation.
  Each line holds the client session, the operation, the planner key, the invoke and complete times, the
  outcome, the status code and the returned `syncVersion`. The outcome is `ok`, `fail` or `unknown`.
  `unknown` marks a request whose response never arrived, so the checker treats its write as possibly
  applied. Without that value, a write that landed behind a dropped connection reads as a stale read.
  Both times are required, because only content saves advance `syncVersion`. Publish, unpublish and
  delete carry no version, so the checker orders them by time.
  REJECTED: Jepsen's two events per operation in EDN — Elle judges transactional isolation. Its fit to
  HTTP operations on planners is unverified, and a converter can add that export later.
  REJECTED: passing in-memory objects to the checker — nobody can replay a verdict from a saved
  history. The checker also stays bound to the process that recorded the history.
  INVARIANT: every time in one history comes from one monotonic clock — unenforced.
  NON-GOAL: an Elle export — it waits until a check shows that Elle fits these operations.

## Takeaway
- takeaway: a history from a faulty network needs a third outcome. A checker that reads a lost
  response as a failure reports violations that never happened.
