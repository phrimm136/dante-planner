# Deploy Manifests — Constraints

- Secrets reach pods ONLY via AWS Secrets Manager → External Secrets Operator → k8s Secret
  (`base/external-secret.yaml`, `base/jwt-secrets.yaml`). Never hand-author a k8s Secret with
  values, and never use the legacy SSM → host-path → volume-mount flow — it targets the
  retired single-EC2 host and has no effect on the k3s fleet.
- Structure is kustomize `base/` + per-region `overlays/`; region differences live in
  overlays, never as conditionals inside base manifests.
- The backend image tag in kustomize is bumped by `.github/workflows/deploy-fleet.yml`
  (GitOps: ArgoCD syncs from `main`) — never hand-edit the tag to deploy.
- RDS/DB endpoints: never commit the PRIMARY (write) endpoint — source it from the Secrets
  Manager runtime-config bundle or an ExternalSecret key. Commit a region-local REPLICA endpoint
  only as its Route53 private-zone name (`*.danteplanner.internal`), and only in the overlay that reads it.
- Auth-Redis env contract: `AUTH_REDIS_HOST` set fleet-wide; `AUTH_LOCAL_REDIS_HOST` absent
  means it aliases to auth (read-local no-op). Setting it wrong fails open — revoked tokens
  get accepted — so treat these two variables as a security surface, not plumbing.
