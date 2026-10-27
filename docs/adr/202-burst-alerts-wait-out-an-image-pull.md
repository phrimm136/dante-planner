# 202 burst-alerts-wait-out-an-image-pull
epic: none · pr: none

## Decisions
- @alerts @pending — The three per-node burst rules (`node-high-disk-write`, `node-high-network-in`, `node-high-network-out`) carry a 15 min pending period. A deploy's backend image pull on the surge node and the existing nodes reads 100 to 145 MiB per 5 min against the 50 MiB inflow threshold, over a baseline of 7 to 9 MiB, and with no pending period the inflow rule fired once per node on every deploy; the write and outflow rules share the shape and stay quiet only because their thresholds are higher. The number is longer than the benign signal's duration, the pull plus the window's tail, under 10 min, and shorter than the time to harm, which for a sustained inflow of this size is unbounded because the CDN and the load balancer stand in front of the nodes.
  REJECTED: a silence the deploy workflow opens for its own window — it misses pulls outside deploys, such as a node replacement.
  REJECTED: deleting the rules — they are the only per-node signal on disk-write and network volume.
  INVARIANT: none of the three rules has a pending period under the benign burst duration — enforcer: the re-check `rg -n 'post_rule "node-high-(disk-write|network-in|network-out)" "15m"'`.

## Takeaway
- takeaway: a threshold on a windowed increase fires on every transient that fills the window, so its pending period is sized from the benign signal's duration and the time to harm, not from the threshold.
