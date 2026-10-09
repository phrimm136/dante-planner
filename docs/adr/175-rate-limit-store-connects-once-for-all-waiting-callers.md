# 175 rate-limit-store-connects-once-for-all-waiting-callers
epic: none · pr: none @rate-limit @redis @degradation

## Decisions
- @rate-limit @redis @degradation — The rate-limit store connects lazily through one attempt at a time
  that every caller arriving meanwhile shares: a caller waits for that attempt's outcome up to the
  command timeout plus 300 ms, and after a failed attempt calls fail at once for five seconds before
  the next attempt may start. Failing fast for every caller during the first connect let eleven of
  twelve first requests after a pod start go unmetered and turned a login in that window into
  `login=unavailable`, while waiting serially behind a lock (the earlier design) made each queued
  request run its own attempt during an outage. The connect's handshake gives up by itself at the
  command timeout, so the wait bound usually returns the attempt's real outcome; when creating the
  client is slow (a cold first use), the attempt can outlast the bound and waiters fail with a
  timeout instead, which the rate-limit interceptor treats the same way. The failed client is shut
  down asynchronously, off every caller's path.
  REJECTED: failing every caller fast while a connect runs — meters nothing and refuses logins in the
  window after each pod start.
  REJECTED: callers queueing on the lock and each connecting in turn — during a blackholed or frozen
  store every queued request pays a full attempt.
  REJECTED: a wait bound picked from a range (one to three seconds) — one second is shorter than a
  measured cold connect on one CPU, and any bound below the connect's own limit hands waiters a
  timeout instead of the outcome.
  INVARIANT: at most one connect attempt runs at a time, every caller other than the one running it
  gets its outcome or fails at the bound, and calls within five seconds of a failure start no
  attempt —
  `LazyConnectingProxyManagerTest`.
  NON-GOAL: bounding the cost of a store that is down — while it stays down, requests arriving during
  each attempt still wait up to the attempt's 3.1 s, roughly one attempt per 8 s.

## Takeaway
- takeaway: share one attempt among concurrent callers and derive their wait from the attempt's own
  limit; fail-fast and plain locking each trade one outage shape for another.
