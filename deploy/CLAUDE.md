# Deploy Manifests — Constraints

- Secrets reach pods ONLY via AWS Secrets Manager → External Secrets Operator → k8s Secret
  (`chart/templates/external-secret.yaml`, `chart/templates/jwt-secrets.yaml`). Never hand-author a k8s Secret with
  values, and never use the legacy SSM → host-path → volume-mount flow — it targets the
  retired single-EC2 host and has no effect on the k3s fleet.
- Third-party components come from their upstream Helm charts, one ArgoCD Application per
  component under `chart/templates/` (chart version in `targetRevision`, values in
  `helm.valuesObject`, raised by Renovate), identical in both regions. The rest is the project's
  own chart, `chart/`; what differs per region is written in `values-<region>.yaml`, which
  `values.schema.json` ties to the region's role.
- The backend image tag in `values-<region>.yaml` is bumped by `.github/workflows/deploy-fleet.yml`
  (GitOps: ArgoCD syncs from `main`) — never hand-edit the tag to deploy.
- RDS/DB endpoints: never commit the PRIMARY (write) endpoint — source it from the Secrets
  Manager runtime-config bundle or an ExternalSecret key. Commit a region-local REPLICA endpoint
  only as its Route53 private-zone name (`*.danteplanner.internal`), and only in the values file of the region that reads it.
- Redis env contract: each region's `backend-config` sets one host per role, bound by relaxed
  name to `redis.<role>.host`: `REDIS_AUTH_HOST` (plus `REDIS_AUTH_PORT` where it is not 6379),
  `REDIS_AUTHLOCAL_HOST`, `REDIS_RATELIMIT_HOST`, `REDIS_SSELOCAL_HOST`. The `AUTH_REDIS_HOST`
  / `AUTH_LOCAL_REDIS_HOST` placeholders in `application.properties` are not set by the fleet,
  so an unset role host resolves to `localhost` and never aliases to auth: auth-local then stays
  open and revocation reads fall back to the auth primary, sse-local never subscribes and
  cross-pod SSE stops. `AUTH_REDIS_PASSWORD` (from `backend-runtime-config`) is the password of
  auth, auth-local and sse-local; rate-limit has none. An auth or auth-local host pointed at a
  reachable store that is not the auth primary or its replica answers "not revoked", and auth
  plus auth-local both unreachable fails open: either way revoked tokens get accepted, so treat
  the auth and auth-local hosts as a security surface, not plumbing.
