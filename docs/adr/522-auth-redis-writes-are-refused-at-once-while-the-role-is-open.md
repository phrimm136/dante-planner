# 522 auth-redis-writes-are-refused-at-once-while-the-role-is-open
epic: none · pr: none · supersedes: 514, 520 @redis @degradation @breaker

## Decisions
- @auth @redis @breaker — Refresh rotation, revocation and tombstone writes reach the auth Redis through its role gate, and while the role is open they are refused at once with a connection failure, without sending the command. An unresponsive auth Redis made each of them wait the three-second command bound: a refresh then answered the auth outage's 503, a tombstone write and a scheduled purge logged and went on, and a logout, a logout of every session and an account deletion ended in a command timeout that no handler maps, so they answered an untyped 500 and raised an error report. A refusal is the connection failure every one of those callers has an answer for, and a command timeout on a write is raised as that same failure, so the three session endpoints answer the auth outage's 503 as well, with the breaker open or not.
  REJECTED: mapping the command timeout in the exception handler — a database query that times out raises the same type, and it would be reported as an auth outage.
  REJECTED: a wire code of its own for a refusal by the breaker — a client can do nothing different with it, and the auth outage already has its code.
  REJECTED: leaving the writes off the gate — it was right while the probe opened a new connection every round and opened healthy roles for seconds at a time; the probe now pings on a connection it holds while the role is closed.
  INVARIANT: a write refused by the gate does not reach the store and fails with the connection-failure type — enforced by the write gate's unit test and by an outage integration test that reads a refused logout's status, code and skip count.
  INVARIANT: a write that times out fails with the connection-failure type too, never with the timeout type a database query also raises — enforced by the write gate's unit test.
  INVARIANT: the rotation, revocation and tombstone stores hold no Redis store handle — enforced by the architecture test's frozen list, which they left.
  NON-GOAL: a probe that misjudges a healthy role — two probe rounds in a row slower than a second open it while the application's own commands would still finish inside their three; until the next round answers, a refresh or a logout answers 503 where it would have succeeded. The refresh token is not consumed, and the next request tries again.
  NON-GOAL: the seconds between the failure and the breaker opening — a write waits the command bound before it fails.
  NON-GOAL: view recording, the SSE publish and subscription, the scheduler lock and the freshness marker store — each needs its own answer for an open role; they stay on the frozen list.

## Takeaway
- takeaway: "the fast path fails like the slow path" has to be checked per caller by exception type; here the slow path's timeout was the one failure nobody mapped.
