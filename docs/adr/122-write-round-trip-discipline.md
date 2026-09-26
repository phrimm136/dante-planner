# 122 write-round-trip-discipline
epic: none · pr: none · supersedes: 009 @writes @round-trip

## Decisions
- @writes @round-trip — Every round trip a write transaction makes to the primary carries a statement the business logic issued or the `COMMIT`; session settings, session-state queries, server-side prepares and the pool's checkout validation ping are bookkeeping and are not allowed. Over JDBC each statement costs a round trip, so a single-round-trip transaction exists only for single-statement work, and a budget nothing can meet enforces nothing; from the secondary region each round trip costs about 95 ms, and bookkeeping was six of a draft save's ten.
  REJECTED: a per-request round-trip budget enforced by a test — every feature that legitimately adds a statement must re-tune it, and each re-tune is indistinguishable from waving a regression through.
  REJECTED: exempting the server-side prepare and the checkout ping — keeps a round trip per transaction plus a prepare per statement on every replaced connection, and a rule stated with exceptions is harder to test than one without.
- @writes @enforcement — The rule is checked by a harness test that counts the primary's command types for each write path, never by review: bookkeeping is invisible in the code that causes it, since the driver, the pool and the ORM each add their own.
- @writes @ping — Liveness moves off the request path to the pool's background keepalive. A connection that died since its last keepalive fails its first statement, and that write surfaces a typed database error (ADR 010) instead of paying a ping on every write.
- @writes @scope — The rule bounds overhead, not work: how many statements and transactions a write needs stays with the N+1 audit of write paths.

## Takeaway
- takeaway: a latency rule is only as real as its unit of measure. "One round trip" named the goal but not a quantity anyone could count; "no round trip without a statement" names one a test can count, and it put the six hidden round trips on the record.
