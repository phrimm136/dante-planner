# 005 deployment-identity-is-observable

epic: none · pr: none

## Decisions
- @deploy @build-info @observability — the running build reports its own commit, threaded as `-PgitSha` from a Dockerfile `ARG` into Flyway-adjacent `springBoot { buildInfo }`. `version` is a constant (`0.0.1-SNAPSHOT`), so build-info without the commit distinguishes nothing. What needs it is a post-deploy gate deciding whether the pods it is measuring are the ones just shipped, and a metric labelled by the build that produced it. REJECTED: polling the DaemonSet's image tag over SSM, which `settle-down` already does — it proves Kubernetes accepted the tag, not that the process answering requests is running that code, and the two diverge exactly when a pull fails and the old container keeps serving. REJECTED: baking the commit into the image tag alone — readable from outside the pod, unreadable from inside it, so the application cannot label its own metrics.
- @deploy @regions @x-served-by — responses carry `X-Served-By`, sourced from the `DEPLOY_REGION` already present in both kustomize overlays. Which region answered was otherwise unobservable from outside the cluster, which leaves geo steering, read-local routing and load-balancer failover assertable only by inferring from latency. REJECTED: a per-region hostname — it would make the region observable by construction, and destroy the single-hostname anycast entry the load balancer exists to provide. REJECTED: reading it from the routing counter in metrics — aggregate, delayed by the scrape interval, and not attributable to the request in hand.

## Takeaway
- takeaway: `DEPLOY_REGION` sat in both ConfigMaps, read by nothing, for as long as the two-region fleet has existed. A value that is configured but never consumed looks identical to one that is wired up, and neither the manifest nor the application will say which.
