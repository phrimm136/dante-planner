# 494 deploy-manifests-are-tested-before-merge
epic: none · pr: none

## Decisions
- @deploy @ci @gates — A pull request that changes `deploy/` runs a PR Gate lane that renders both overlays with a pinned kustomize, validates the output against the Kubernetes and CRD schemas the tree uses with a pinned kubeconform, and serves the rendered HTTPRoutes through the Traefik image the controller manifest names, on a pinned throwaway k3s to a header-echo backend, asserting each route's contract with HTTP requests. ArgoCD applies main as soon as it lands, so before this lane a manifest change reached prod with no check at all, and a route's behavior (a filter, a rewrite) is invisible to rendering and schema checks alike.
  REJECTED: render and schema checks only — they pass a valid route that does the wrong thing, which is the class of change the edge actually carries.
  REJECTED: testing against the standing staging cluster — it is shared with the release rehearsal and reachable only through the edge provider, so a pull request's manifest cannot be applied there in isolation.
  INVARIANT: every rule of every rendered HTTPRoute that targets the backend has a behavior assertion — the edge behavior test fails on a backend route rule it does not know, and applies the render into the namespace the fleet syncs to, so a route without a namespace cannot slip past it; a row's probe path must fall under its own rule's path match, but a more specific rule elsewhere can still win that path, which the check does not detect.
  NON-GOAL: testing the Traefik controller's own deployment (host networking, TLS listener, DaemonSet shape) or SSE streaming through the edge — the harness runs Traefik as a plain Deployment on an HTTP listener; those stay with the deploy pipeline.
  NON-GOAL: holding a deploy until the gate passes — ArgoCD still tracks main; a deploy branch promoted after PR Gate is a separate change to the GitOps target.

## Takeaway
- takeaway: a config change that only a running system can interpret needs a running system in its test, even a throwaway one.
