# 209 the-app-namespace-enforces-restricted-pod-security
epic: none · pr: none

## Decisions
- @pod-security @namespaces — The `danteplanner` namespace moves to Pod Security Admission `enforce: restricted`, and the only workload that needs the host, node-exporter, moves to its own namespace `privileged-exporter` at `enforce: privileged`. The kyverno CLI v1.13.4 run over both rendered overlays finds all 12 workloads failing restricted, and node-exporter (hostNetwork, hostPID, hostPath), traefik (hostNetwork, hostPort) and cloudflared (hostNetwork) also fail baseline. Re-check: `docker run --rm -v "$PWD/deploy:/deploy:ro" registry.k8s.io/kustomize/kustomize:v5.8.1 build /deploy/overlays/oregon | rg -c 'hostNetwork: true'`.
  REJECTED: keeping node-exporter in `danteplanner` and moving every other workload to a new namespace — every later workload would also have to be placed outside the default namespace by hand.
  REJECTED: enforcing `baseline` only — node-exporter fails baseline as well, so it still needs its own namespace, and baseline leaves privilege escalation, capabilities and root users unchecked.
  INVARIANT: `danteplanner` carries `enforce: restricted` only after every pod applied into it passes restricted, the overlays' workloads and the staging rehearsal manifests applied by hand alike — unenforced until the label exists, then enforced by the label itself.
  NON-GOAL: readOnlyRootFilesystem — restricted does not require it.
- @pod-security @edge — Traefik is reached through a ClusterIP Service and neither Traefik nor cloudflared uses hostNetwork. Global Accelerator is gone (`aws globalaccelerator list-accelerators --region us-west-2` returns `[]`), so nothing outside the cluster needs the host's port 443, and the tunnel's origin becomes the Service name.
  REJECTED: keeping hostNetwork and enforcing restricted elsewhere only — the edge would need the privileged namespace, which then holds internet-facing pods.
  NON-GOAL: where the edge runs — ADR 207 decides the node.

## Takeaway
- takeaway: a namespace's policy level is a property of its weakest pod, so isolate the pods that need the host and let the policy follow the rest.
