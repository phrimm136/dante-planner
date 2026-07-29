# 009 replica-routing-and-connection-pools
epic: none · pr: none

## Decisions
- @routing @readonly @transactions — `@Transactional(readOnly = true)` is the replica-routing signal, resolved through `AbstractRoutingDataSource` behind a `LazyConnectionDataSourceProxy`; any write performed by a nominally-read endpoint is extracted so the annotation can be applied. REJECTED: leaving an incidental write inside a read path — it forces the whole transaction onto the primary, and from the secondary region that costs roughly 130ms per statement rather than sub-millisecond replica reads.
- @pools @capacity — Connection pools are sized from measured transaction duration rather than copied between deployments: primary pools are deliberately smaller in the remote region because WAN transactions hold a connection far longer per unit of work, and the worst case with every autoscaling group at maximum must still fit under the instance's connection ceiling with a reserve. REJECTED: carrying a single-pod pool size into a multi-pod fleet — pool size multiplies by pod count, and the ceiling is a property of the database instance, not of the application.
- @writes @round-trip — Write transactions must complete in a single round trip, and an N+1 audit of write paths gates the second region serving traffic. REJECTED: accepting per-statement chattiness in writes — cross-region latency compounds per statement, so a pattern that is invisible in one region is a multi-second stall in the other.

## Takeaway
- takeaway: a latency number turns previously-equivalent code shapes into different code. Statement count and connection-hold time are free locally and are the dominant cost across a WAN, so both need an explicit budget once a second region exists.
