# 182 reads-pick-a-healthy-source
epic: none · pr: none · supersedes: 168 @reads @outage @trigger, 168 @reads @outage @acquire, 130 @reads @outage @cost

## Decisions
- @reads @sources — A read-only transaction picks its source from an ordered list per region,
  each source marked authoritative or not. Seoul reads its replica first and the primary second.
  Oregon reads the primary first and the Seoul replica second. The region difference is this list,
  not a separate code path, so a Seoul replica outage moves Seoul's reads to the primary. Each pod
  probes each source in its list every second with a fresh connection, bounded at one second. The
  probe runs outside the Hikari pool, on its own single-thread scheduler. Two consecutive failures
  open that source's breaker, one success closes it, and user reads never act as half-open probes.
  When a read cannot get a connection from the chosen source, that breaker records the failure and
  the same request reads from the next source. Only a connection failure moves a read; any other
  acquire failure inside a read-only transaction, such as a saturated pool, answers the read code
  (@reads @degrade) on the same source. A source that is not first in its list reads through its
  own small pool, so a fallback never draws on the pool writes use; Seoul's primary read pool also
  carries the reads the GTID gate pins to the primary. A request whose GTID cookie is usable while
  the primary is forecast reads the primary pinned and never falls back, because the breaker
  forecast can still be closed when the acquire fails. The selection records the chosen source for
  the transaction, and the read-path guards run whenever that source is not authoritative.
  REJECTED: a replica branch in Seoul and a fallback data source in Oregon — two paths with guards
  keyed to the region flag. Oregon's fallback served deleted planners, missed created ones, and lost
  an author's own save, measured by the two-region consistency run.
  REJECTED: switching the guards on in Oregon only while its breaker is open — a single failed
  acquisition also falls back with the breaker closed.
  REJECTED: a replica in Oregon — a second database billed every month for an outage of minutes a
  few times a year (ADR 092).
  REJECTED: user reads as half-open probes — a failed probe is a failed user read.
  REJECTED: one probe for the cluster behind ShedLock — each pod needs its own view of its path.
  REJECTED: the Hikari pool for the probe connection — an acquire waits the pool's timeout, and an
  idle pooled connection proves nothing about a new connect.
  REJECTED: retrying the chosen source once before moving on — it doubles the wait on a dead
  source.
  REJECTED: falling back to the primary through the write pool — a Seoul replica outage would send
  every Seoul read into the ten connections Seoul's writes need, and about 71 requests per second
  saturate them (ADR 008).
  REJECTED: deciding the GTID gate from the breaker forecast alone — a read forecast for the
  primary that then fails its acquire fell back to a replica behind the author's save.
  INVARIANT: an unpinned read whose acquire fails with a connection failure is served by the next
  healthy source, and the transaction records which source served it; a pinned read never moves —
  `ReadSourceSelectorTest`, `ReadOnlyRoutingDataSourceTest`, `ReadSourceOutageIT`.
  INVARIANT: every read served by a non-authoritative source passes the read-path guards —
  `ConsistencyRunIT` across both regions.
  NON-GOAL: moving a read after its connection is lent — a statement that fails mid-query cannot
  change source inside its transaction.
  NON-GOAL: a breaker state shared across pods — each pod opens and closes on its own evidence.
- @reads @degrade — When a read-path guard needs an authoritative source and none is healthy,
  the read answers 503 with code `READ_TEMPORARILY_UNAVAILABLE` and `Retry-After`. ADR 123 promises
  that a misrouted request is slower, never wrong, and during an outage the stale answer is the
  wrong one. Statements a pod sends to a source in the other region cost one ocean round trip each,
  so a read that leaves its region is slower by its statement count, never by a fixed amount.
  REJECTED: serving the replica's answer during the outage — it shows deleted planners, hides
  created ones and loses the author's own save.
  REJECTED: reusing `WRITE_TEMPORARILY_UNAVAILABLE` — the client reads it as a failed write.
  NON-GOAL: availability of a read whose guard needs an unreachable authoritative source — it
  answers 503 until one is reachable.
- @reads @sources @log — A probe logs a breaker's open and close transitions at INFO. A rolling
  restart opened the breaker dozens of times in one deploy, each open a WARN, and the generic warn
  alert turned back on the per-open alert that ADR 168 rejected; a sustained open stays with the
  breaker metric rule.
  REJECTED: a WARN once the open state persists — a second, slower copy of the breaker metric rule.
  INVARIANT: no probe logs a breaker transition above INFO — an `rg 'log\.warn'` over the
  read-source and marker-source probes prints nothing.

## Takeaway
- takeaway: attach a guard to the property of the source it protects, not to the deployment that
  usually reads it. A path that runs only in an outage otherwise inherits none of the guards.
