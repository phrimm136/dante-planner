# 183 every-read-reflects-every-answered-write
epic: none · pr: none

## Decisions
- @freshness @marker — Each scope a write touches holds a marker in Redis: a hash from primary
  server uuid to the highest GTID sequence number of an answered write on that scope. Scopes are
  one entity, one owner's planner list, the public catalog (every public and recommended list and
  filter), one author (the user row that planners and comments render), one planner's comment
  thread, one user's inbox, one user's settings, and the moderation lists. A write declares the
  rows it changed, and a read reads the author marker of every author it renders, so an epithet
  change or an account change raises one marker however many planners the user has. At response
  time the request raises every touched scope's marker to the GTID union of its
  commits with one Lua call (a max per uuid), then waits for one Redis replica to acknowledge
  (`WAIT`), and only then answers and sends any push that announces the write. `WAIT` counts only
  the writes of the connection it runs on, so each pod raises markers on one dedicated, long-lived
  connection: concurrent requests queue their raises, one thread sends a batch and a single `WAIT`
  for it, then releases every request in the batch. GTIDs are captured
  only when a non-readOnly MySQL transaction commits.
  REJECTED: an author change declaring each of the user's planners — the write's marker count
  grows with the user's planners, and the comment threads that render the author are missed.
  REJECTED: a marker stored in MySQL — it replicates with the very lag it is meant to reveal.
  REJECTED: reading markers from the Redis primary on every Seoul read — one ocean round trip per
  read, where `WAIT` costs one per write and writes are rare.
  REJECTED: a single sequence number per scope — a promoted primary restarts the sequence under a
  new uuid, and a max over raw numbers then ignores every newer write.
  REJECTED: a pending entry before commit — it guards only writes that never answered, which the
  contract does not cover, and adds a state every reader must handle.
  REJECTED: a push sent before `WAIT` — its receiver can read before the marker reaches its region.
  REJECTED: `WAIT` on the shared application connection — every Redis command of the pod queues
  behind it for up to the bound.
  REJECTED: a new connection per raise — a connect, authentication and script load on every write,
  and client thread pools that multiplied with every application context (1,540 Lettuce threads in
  one test run).
  REJECTED: a connection pool — a new dependency, and connections grow with a burst of writes where
  a batched `WAIT` costs one round trip for all of them.
  NON-GOAL: writes that answer no request (the scheduled view-count flush, the keyword backfill,
  the account purge after its grace period) — there is no response to bind a read to, so they stamp
  nothing and appear when replication applies them. The OAuth reactivation answers a request and
  stamps like any write.
  INVARIANT: two writers on one scope never lower its marker — `MarkerStoreIT`.
  INVARIANT: every write that changes a planner scope declares that scope — `FreshnessDeclarationIT`
  for the writes it lists; a gate that fails any committing request without a declaration is
  unenforced until every write declares its scopes.
- @freshness @store-health — A marker failure does not fail the write: `WAIT` has an upper bound,
  and the write answers when it passes. The marker store has an ordered source list per region,
  like the database (182 @reads @sources): Seoul reads its Redis replica first and the Oregon Redis
  primary second; Oregon reads only its own store, the primary. Each pod probes each store every second and opens a breaker
  on two consecutive failures. A broken replication link does not make markers unknown, because
  every write answered after a successful `WAIT` already reached the Seoul replica. A read answers
  503 only when its marker names a change that no reachable source has applied and no
  authoritative database is reachable, or when no marker store and no authoritative database is
  reachable. Grafana shows marker-store health per pod and source and `WAIT` timeouts, and both
  alert.
  REJECTED: failing the write when `WAIT` times out — one lost Redis replica would fail every write
  in both regions, and the client would retry a write that already committed into a version
  conflict.
  REJECTED: treating every marker as unknown while the replication link is down — when Oregon or
  the link between regions fails, every Seoul read then needs the unreachable primary and answers
  503.
  REJECTED: answering success and ignoring a failed local store — reads would skip the markers and
  serve stale state with no signal.
  REJECTED: a second marker source for Oregon (the Seoul replica) — Oregon reads markers only after
  its database primary failed, the second source would matter only if its Redis primary failed at
  the same time, and reaching the Seoul replica needs a new cross-region endpoint, DNS record and
  security rule.
  NON-GOAL: Oregon reads while both its database primary and its Redis primary are down — markers
  are unknown and no authoritative database is reachable, so they answer 503.
  NON-GOAL: a write answered after a `WAIT` timeout — its marker may be missing from the Seoul
  replica, so Seoul can serve its earlier state until replication catches up; `WAIT` timeouts
  count it.
- @freshness @ttl — A marker expires one hour after its last raise. Expiry is cleanup, not the
  correctness gate, and it is safe only while replica lag stays under an hour, so an alert fires
  when the Seoul replica lags 60 seconds or more.
  REJECTED: a marker that never expires — keys grow with every entity ever written.
  NON-GOAL: sub-second lag monitoring — the alert needs seconds, and the wait histogram gives
  milliseconds where the bound needs them.

## Takeaway
- takeaway: mark what changed with the commit that changed it, and let the replica prove it caught
  up. A causal token held by the writer protects only the writer; one held by the data protects
  every reader.
