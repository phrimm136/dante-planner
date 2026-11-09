# 183 every-read-reflects-every-answered-write
epic: none · pr: none · supersedes: 008 @ryw @gtid @cookie, 008 @gtid @capture, 008 @replica @miss @byid, 008 @replica @bulkhead, 008 @delete @tombstone, 008 @read-path @contract, 130 @reads @gtid, 130 @reads @gtid @errors, 151 @outbox @tombstone @readpath, 151 @withdraw @tombstone, 151 @comments @visibility

## Decisions
- @freshness @contract — A read that starts after a write's response reflects that write, for
  every reader, in every region, on every read path: by-id reads, lists, filtered lists and their
  membership, comment threads, the notification inbox, settings and the moderation lists. The only
  other answer it may give is 503 `READ_TEMPORARILY_UNAVAILABLE`. A read that starts before the
  response may return either state. Every write a user expects to see the result of is covered:
  planners, comments, upvotes, notifications, settings and moderation actions.
  REJECTED: read-your-own-writes for the author only, through a GTID cookie — it misses every
  other reader, every write the server makes on a user's behalf, and the Oregon fallback path, all
  measured by the two-region consistency run.
  REJECTED: masking only removals (delete tombstones and withdrawal markers) — an edit still shows
  its old text and a new row still misses its list.
  REJECTED: accepting list absence as eventual — a filtered list then disagrees with the detail
  page the same reader opens next.
  INVARIANT: no read that starts after a response returns the state before it — `ConsistencyRunIT`
  for planners and lists; unenforced for threads, the inbox, settings and moderation until the
  consistency run judges them.
  NON-GOAL: view counts — they are buffered in Redis and flushed later (ADR 131), so no reader
  expects them at once.
  NON-GOAL: a write that committed but never answered — its writer saw an error, so no reader is
  promised anything, and it appears when the replica catches up.
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
- @freshness @read — A read on a non-authoritative source (182 @reads @sources) reads the markers of
  the scopes it touches. No marker: serve. A marker the source has applied: serve. A marker it has
  not applied: wait on that source for the GTID set, up to a bound set to the cost of the alternative, one
  statement against the next source (250 ms at the start), and tuned from the exported wait-time
  histogram, then move to the next source. A marker store that
  errors or is unhealthy: treat every marker as unknown and read an authoritative source. With no
  healthy authoritative source the read answers 503 (182 @reads @degrade). A by-id read loads the
  row without filtering on its visibility (publication, the author's account state) and decides
  visibility in code after the markers, so it always knows the author whose marker it reads; a
  list keeps visibility in SQL, since paging needs it there. A by-id miss (no row) with no entity
  marker is a final 404, because a create raises its entity's marker. Each pod remembers, per
  source, the highest GTID set it has seen applied, and a marker inside it needs no probe.
  REJECTED: falling to the primary after a short probe (the cookie gate's 50 ms) — a Seoul read on
  the primary costs one ocean round trip per statement, so waiting for the replica is cheaper up to
  that many round trips.
  REJECTED: filtering by-id visibility in SQL — a row hidden by a lagging author state returns
  no author id, so the read cannot find the author marker that a reactivation raised, and a
  second owner lookup on every miss adds a query.
  REJECTED: re-checking every by-id miss on a primary bulkhead pool — the marker already says
  whether a recent create exists, and junk ids no longer reach the primary.
  REJECTED: per-comment markers with an eventual thread membership — a new comment then misses the
  thread its author's readers open next.
  INVARIANT: a remembered applied GTID never runs ahead of its source — replica apply is monotonic;
  unenforced by a test.
- @freshness @read @boundary — The marker decision runs as one around-advice on the outermost
  `@Transactional(readOnly = true)` service method that returns `ScopedRead`, ordered just outside
  the transaction interceptor, and it retries the method on the next source when a marker says
  skip. Each attempt needs its own transaction and connection, so the decision cannot run inside
  the method. A result type or a not-found exception declares the scopes the read touched. An
  inner call joins its caller's transaction and never decides. The advice exists only when read
  routing is on. Marker reads for all scopes go to each marker source as one dispatched batch.
  REJECTED: a retry loop at each controller call site — nothing enforces it, so a new read skips it
  without failing anything.
  REJECTED: Spring's pipelined execution for the marker batch — it opens a dedicated connection on
  every call, which costs more than one command for the common one- or two-scope read.
  INVARIANT: every read-only service method declares exactly one of a `ScopedRead` return,
  mandatory propagation, a pending marker with a reason, or an exemption with a reason, and a
  `ScopedRead` return carries its read-only annotation on the method itself, where the advice
  matches it — `FreshnessReadCoverageTest` over the production classes the advice can advise.
  INVARIANT: the count of pending reads only goes down; each one leaves when its scope arrives —
  `FreshnessReadCoverageTest`, which pins the count.
  INVARIANT: the advice runs outside the transaction — `FreshnessReadAspectTest`, which fails when
  the transaction advisor is ordered first.
  INVARIANT: one marker read sends one batch per marker source — `MarkerStoreIT`.
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
- @comments @visibility — A comment thread follows its planner's public view: a reader other than
  the owner gets the planner-not-found answer for an unpublished planner, and the thread's public
  push fires only while the planner is still published. The owner's own comment notification
  ignores publication; a reply notification to any other parent author fires only while the
  planner is published, since it names the planner and quotes the reply to someone who would
  otherwise get not-found. Thread freshness, including the planner's withdrawal, comes from the
  thread and catalog markers.
  REJECTED: a forbidden answer for another reader — it confirms that a private planner exists,
  which the planner read itself refuses to do.
  INVARIANT: an unpublished or withdrawn planner's thread is unreadable to non-owners on every read
  path — the thread contract test and the replica-lag test.

## Takeaway
- takeaway: mark what changed with the commit that changed it, and let the replica prove it caught
  up. A causal token held by the writer protects only the writer; one held by the data protects
  every reader.
