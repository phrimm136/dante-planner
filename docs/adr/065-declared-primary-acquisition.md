# 065 declared-primary-acquisition

## Decisions

- @routing @readonly @observability — A PRIMARY connection acquired while no transaction is
  active is a defect, not a shortcut: the routing key is derived from the transaction's
  read-only flag, so an acquisition without one can never reach the replica and holds a
  primary connection for work nobody declared. The routing datasource judges every
  resolved-to-PRIMARY acquisition, counting it as `datasource.primary.undeclared` and
  rejecting it outright where a red test is wanted.
  REJECTED: leaving the case to review — the annotation that routes and the annotation that
  bounds a transaction are the same one, so the omission reads as an optimization forgone
  rather than a route lost, and nothing in the request's behaviour reveals it.
- @routing @boot — The judgement arms at application-ready rather than at bean creation, so
  migrations, schema validation and pool-metric binding are exempt by having already run.
  REJECTED: a static allowlist of boot-time actors — every framework that touches the
  datasource before the first request would have to be enumerated and re-enumerated on each
  upgrade, and an allowlist that names callers also excuses them forever after boot.
  REJECTED: exempting the whole non-request path — scheduled work is exactly where an
  undeclared primary read hides longest.
- @routing @config — Rejection is environment-driven and defaults to off, on in the
  integration tier. The counter is the production posture because a rejected acquisition
  turns a served request into a 500 for a fault that is not the caller's; the tier that owns
  a red feedback loop takes the throw.
  REJECTED: rejecting everywhere — the first undiscovered call site becomes an outage.
  REJECTED: counting everywhere — a counter nobody is paged on is a criterion nobody meets.
- @pools @observability — The three routing pools are beans rather than locals of the
  datasource factory method, each carrying its own pool name.
  REJECTED: hand-registering pool gauges — Boot already binds `hikaricp_*` onto every
  `DataSource` bean, and a hand-rolled binding would drift from the meter names the
  dashboards and every other Boot service already use.
- @pools @gtid — The GTID-capturing wrapper answers `unwrap` from the pool it wraps. A
  wrapper below the routing layer is on the path between Boot's binder and the pool, and one
  that dead-ends the JDBC wrapper contract silently costs every meter behind it.
  REJECTED: moving the wrapper above routing to keep the pool bare — the committed GTID is
  session state on the connection that committed, and only the layer holding that connection
  can read it.

## Takeaway

- takeaway: an invariant that only a human reviewer can check is a wish. The cheapest way to
  make one enforceable is often a phase boundary the runtime already publishes — arming after
  ready separates "the framework is starting" from "we are serving" without naming a single
  actor on either side.
