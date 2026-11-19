# 521 the-role-probe-keeps-the-sse-subscription
epic: none · pr: none · supersedes: 514 @sse @redis

## Decisions
- @sse @redis @probe — The sse-local role counts as available only while this pod's SSE subscription is live: the subscriber is a connector of the role probe, which asks it every round and, when it is not listening, has it subscribe again on a thread of its own. The listener container never retries a failed first subscription and ignores a second start until it is stopped, so a pod that started while Redis was silent stayed deaf to other pods' events until it restarted; subscribing blocks its caller for up to the three-second connection bound, longer than the probe's one-second round.
  REJECTED: subscribing again when the sse-local breaker closes — a pod where PING answered all along and only the subscription failed never sees its breaker close, so it would never subscribe.
  REJECTED: a watcher of its own for the subscriber — it needs a scheduler, a gauge and an alert rule to do what the probe, the role breaker and the open-breaker alert already do.
  REJECTED: holding readiness until the subscription is live — every pod in a region shares that Redis, so an outage during a rollout would leave the region's API unready for the sake of live notifications.
  INVARIANT: a pod that is not subscribed keeps its sse-local breaker open — enforced by a probe test over a subscription that cannot be made.
  INVARIANT: at most one subscription attempt runs at a time, and the probe never runs it and never waits for it past its round's deadline — enforced by the subscription's unit test.
  INVARIANT: the subscription state never changes readiness or liveness — enforced by an integration test that reads both while the subscription cannot be made and the sse-local breaker is open.
  NON-GOAL: a subscription lost after it was live — its connection reconnects and subscribes again on its own, and the container reports listening throughout, so the probe does not see the gap.

## Takeaway
- takeaway: when a component must be watched and restarted, give the watcher that already exists one more thing to ask; a state it cannot reach stays visible through the signal it already raises.
