# 123 write-forwarding-to-the-primary-region
epic: none · pr: none

## Decisions
- @writes @routing — Every non-GET request to the API is routed by one load-balancer rule to the region that holds the primary database, while reads keep geo steering. From the secondary region a write pays the WAN round trip once per statement (19–25 for a published save at about 95 ms each); forwarded, it pays the ocean once at the edge (about 300 ms more from Korea) and runs its statements beside the primary, a published save measured by its parts at about 0.7 s against 2.9 s.
  REJECTED: trimming round trips in place (ADR 122's driver and pool work, folding the filter rebuild into the save) — each step saves about 95 ms and the floor stays near five round trips across the ocean.
  REJECTED: a secondary-region application proxy forwarding writes over the peering link — close to the same latency, bought with an application-level proxy path carrying its own authentication, timeouts and error mapping, where the edge needs one rule.
  REJECTED: serving Korea from an in-country edge (Enterprise plan or a second CDN) — shortens the client leg but leaves the per-statement ocean crossing, and costs a plan or a second vendor.
- @writes @routing @expectation — Routing is a performance expectation, not a correctness precondition: both regions can serve every request, so a misrouted request is slower, never wrong. The expectation is that the method states the effect, GET reading and every other method writing. The GETs known to write are accepted as they are: refresh-token rotation inside the authentication filter, one Redis round trip once per access-token lifetime, and the OAuth callback on login.
- @writes @routing @rule — The rule matches the method and overrides `region_pools` for every mapped region, `default_pools`, and `session_affinity: none`, each with the primary's pool first. Geo steering consults the region map before the default pools, so a rule that overrides only `steering_policy` and `default_pools` matches and routes nothing; the affinity override lets a client pinned to the secondary pool still send its writes to the primary's.
  REJECTED: overriding `steering_policy` and `default_pools` — verified against production to match and change nothing.
- @writes @routing @reads-as-post — A read shaped as a POST becomes a GET rather than an exception in the rule: the planner batch pull moves to GET with its ids, at most 50, in the query string. The POST stays beside it until the frontend release that uses the GET is live, and is removed then. The GET also needs no CORS preflight.
  REJECTED: excluding the batch path in the rule's condition — a path list is an exception every new endpoint can forget.
- @writes @routing @geo — ADR 091's region map remains the read path's steering; the rule is layered on it for writes only and names no region of its own.
- @writes @routing @views — A corollary for ADR 116: its `POST .../viewcount` is forwarded, so the view flush runs beside the primary instead of costing the secondary region a WAN transaction per detail read.
- @writes @routing @rollout — The rule lives in the Cloudflare Terraform stack. It is enabled first scoped to a probe header, then for every write once the batch GET release is live, and removing it is the rollback. Staging has no load balancer, so the header-scoped rule on production is the canary.

## Takeaway
- takeaway: latency is distance times sequential trips; when the trips cannot fall much below five, moving the work next to its data beats trimming the work, and the right routing signal is the one the request already carries.
