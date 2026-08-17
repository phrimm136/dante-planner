# 006 fleet-topology-and-redundancy
epic: none · pr: none

## Decisions
- @regions @topology @availability — Two regions, Oregon primary and Seoul secondary, bought for latency, read redundancy and warm DR, explicitly NOT active-active. REJECTED: active-active writes — a Seoul write requires Seoul ∧ peering ∧ Oregon, so adding the region multiplies the failure surface of a write instead of adding redundancy to it. Seoul becoming primary someday is the cross-region replica promote runbook, executed deliberately.
- @k3s @cost @operability — k3s on EC2, one server per cluster. REJECTED: EKS — ≈$73/mo/region for the control plane alone, and the managed plane removes exactly the operational surface this fleet exists to exercise.
- @etcd @quorum @raft — Two independent clusters; etcd is never stretched across regions. REJECTED: one stretched cluster — Raft election budgets do not survive a 130ms RTT. REJECTED: a third-region etcd witness — split-brain is a quorum-layer problem and these clusters share no quorum, so a witness solves nothing here.
- @redundancy @cost (taste) — Instance-level redundancy is spent only on the tier that serves user requests (the app ASG). Singleton infra nodes (ingress, control plane, data) fail over at region granularity via the global router. REJECTED: per-tier redundancy everywhere — pays for standby capacity on components whose failure the router already covers.
- @nodes @lifecycle — App nodes are cattle, joining via ASG user-data and an SSM token; control-plane, ingress and data nodes are pets with EC2 auto-recovery alarms. The ASG is the only scaling dial, so node count equals pod count, and the surge headroom doubles as deploy headroom.

## Takeaway
- takeaway: a redundancy budget spent per component buys standby capacity for failures the layer above already absorbs. Spend it where a failure is visible to a user, and let everything else fail over at the granularity the router already operates on.
