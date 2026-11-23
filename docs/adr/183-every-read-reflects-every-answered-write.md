# 183 every-read-reflects-every-answered-write
epic: none · pr: none · supersedes: 008 @ryw @gtid @cookie, 008 @gtid @capture, 008 @replica @miss @byid, 008 @replica @bulkhead, 008 @delete @tombstone, 008 @read-path @contract, 130 @reads @gtid, 130 @reads @gtid @errors, 151 @outbox @tombstone @readpath, 151 @withdraw @tombstone, 151 @comments @visibility, 180 @consistency-checker @observability

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
  for planners, lists, comment threads and settings; `FreshnessReadIT` for the inbox, the
  moderation lists and the role and account reads, which the run's single user cannot exercise.
  NON-GOAL: view counts — they are buffered in Redis and flushed later (ADR 131), so no reader
  expects them at once.
  NON-GOAL: a write that committed but never answered — its writer saw an error, so no reader is
  promised anything, and it appears when the replica catches up.
- @freshness @marker — Each scope a write touches holds a marker in Redis: a hash from primary
  server uuid to the highest GTID sequence number of an answered write on that scope. Scopes are
  three: one planner (its row and its comment thread), one user (the user row that planners and
  comments render, and everything only that user lists: their planners, settings and inbox), and
  Global (every list many users share: the public and recommended lists and their filters, the
  moderation lists, and the publish notification fan-out, whose recipients the write does not
  know). A write declares the rows it changed, and a read reads the user marker of every author it
  renders, so an epithet change or an account change raises one marker however many planners the
  user has. At response
  time the request raises every touched scope's marker to the GTID union of its
  commits with one Lua call (a max per uuid), then waits for one Redis replica to acknowledge
  (`WAIT`), and only then answers and sends any push that announces the write. A dispatch of an outbox
  event is a window of its own: it raises after its commit and before it sends its pushes, and it
  declares the recipient's scope for a notification row and the scope each push announces, since
  the request that caused it raises only at its own response, which the dispatch can overtake. `WAIT` counts only
  the writes of the connection it runs on, so each pod raises markers on one dedicated, long-lived
  connection: concurrent requests queue their raises, one thread sends a batch and a single `WAIT`
  for it, then releases every request in the batch. GTIDs are captured
  only when a non-readOnly MySQL transaction commits.
  REJECTED: an author change declaring each of the user's planners — the write's marker count
  grows with the user's planners, and the comment threads that render the author are missed.
  REJECTED: a scope per screen (owner list, author, thread, inbox, settings and moderation apart)
  — with about one write in ten minutes and a replica that applies it in milliseconds, a finer
  split saves no measurable wait, and every extra kind is one more scope a write or a read can
  forget.
  REJECTED: one marker for the whole site — while the replica lags past the read wait bound, every
  Seoul read after any write moves to the primary, whose Seoul read pool holds three connections.
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
  REJECTED: a build rule that every write handler carries a declaration — which scopes a write
  changed depends on what it found (a published save adds Global, an unchanged publish declares
  nothing), so a declaration's presence on a method proves little.
  INVARIANT: every write declares the scopes it changed, or that it changes none —
  `FreshnessDeclarationIT` for the exact scopes of each write it lists; a stamp window that commits
  a write MySQL's own GTID tracking reported and holds no declaration throws in the test profile
  and counts in production. The gate sees only what a window stamps: a write outside any window, and an
  effect inside a dispatch window, which starts as declared-none for its own bookkeeping, pass it.
- @freshness @read — A read on a non-authoritative source (182 @reads @sources) reads the markers of
  the scopes it touches. No marker: serve. A marker the source had applied before the read loaded: serve. A marker it had
  not applied: wait on that source for the GTID set, up to a bound set to the cost of the alternative, one
  statement against the next source (250 ms at the start), and tuned from the exported wait-time
  histogram. If the source applies it within the bound, run the read again there, because the first
  load may predate the write; otherwise move to the next source. A read runs again on one source
  at most once: a newer marker it has still not applied after that moves the read on without a
  second wait, so one source costs at most two loads and one wait. A marker store that
  errors or is unhealthy: treat every marker as unknown and read an authoritative source. With no
  healthy authoritative source the read answers 503 (182 @reads @degrade). A by-id read loads the
  row without filtering on its visibility (publication, the author's account state) and decides
  visibility in code after the markers, so it always knows the author whose marker it reads; a
  list keeps visibility in SQL, since paging needs it there. A by-id miss (no row) with no entity
  marker is a final 404, because a create raises its entity's marker. Each pod remembers, per
  source, the highest GTID set it has seen applied, and a marker inside the set remembered before
  the read loaded needs no probe.
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
  mandatory propagation, or an exemption with a reason, and a
  `ScopedRead` return carries its read-only annotation on the method itself, where the advice
  matches it — `FreshnessReadCoverageTest` over the production classes the advice can advise.
  INVARIANT: the advice runs outside the transaction — `FreshnessReadAspectTest`, which fails when
  the transaction advisor is ordered first.
  INVARIANT: one marker read sends one batch per marker source — `MarkerStoreIT`.
- @freshness @store-health — A marker failure does not fail the write: `WAIT` has an upper bound,
  and the write answers when it passes. The marker store has an ordered source list per region,
  like the database (182 @reads @sources): Seoul reads its Redis replica first and the Oregon Redis
  primary second; Oregon reads only its own store, the primary. Each store is a Redis role, and a read
  skips a store whose role breaker the role's probe has opened (514 @redis @degradation @breaker). A broken replication link does not make markers unknown, because
  every write answered after a successful `WAIT` already reached the Seoul replica. A read answers
  503 only when its marker names a change that no reachable source has applied and no
  authoritative database is reachable, or when no marker store and no authoritative database is
  reachable. Grafana shows the Redis role breakers per pod and role and `WAIT` timeouts, and both
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
  planner's marker.
  REJECTED: a forbidden answer for another reader — it confirms that a private planner exists,
  which the planner read itself refuses to do.
  INVARIANT: an unpublished or withdrawn planner's thread is unreadable to non-owners on every read
  path — the thread contract test and the replica-lag test.

- @freshness @removal — The GTID cookie, its read gate and the primary pin are removed: a read's
  freshness is decided by the markers alone. Every read and write declares its scopes, so the gate
  added nothing a marker does not answer.
  REJECTED: keeping the cookie as the writer's cover when a raise fails — it protects only that
  writer, and only until the cookie is read once.
  INVARIANT: a write whose raise outlived its caller's bound is counted, whether or not the marker
  lands later — `freshness.marker.raise.abandoned`, `RaiseBatcherTest`.
  NON-GOAL: a writer's own reads after a failed or abandoned raise — the answer can be stale until
  replication applies the write; the counter shows how often.

## Takeaway
- takeaway: mark what changed with the commit that changed it, and let the replica prove it caught
  up. A causal token held by the writer protects only the writer; one held by the data protects
  every reader.
