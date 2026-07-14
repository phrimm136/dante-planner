# deploy/ — GitOps manifests (kustomize base + overlays)

One repo, kustomize `base/` + `overlays/{oregon,...}`, synced by ArgoCD core (per cluster).
The CP node bootstraps ArgoCD and applies `argocd/root-app.yaml`, which points ArgoCD at
`overlays/oregon`; from then on the cluster is GitOps-managed.

## Layout

```
deploy/
  argocd/root-app.yaml     ArgoCD Application → overlays/oregon (app-of-apps root)
  base/                    region-invariant manifests
    spring-daemonset.yaml  Spring backend, DaemonSet on role=app nodes
    spring-service.yaml    backend Service + ConfigMap
    traefik-gateway.yaml   Gateway API: Gateway + app HTTPRoute + /healthz-local route
    external-secret.yaml   ESO SecretStore + ExternalSecret (RS256 private key ← Secrets Manager)
    jwt-secrets.yaml       ExternalSecrets: RS256 public key + AES-256 encryption key ← Secrets Manager
    redis-auth.yaml        auth-state Redis StatefulSet on role=data (AOF, no auto-promote)
    redis-ratelimit.yaml   ephemeral rate-limit Redis on role=data (no durability)
    prometheus.yaml        Prometheus on role=data (local scrape)
  overlays/oregon/         region-specific values (image, namespace, endpoints)
```

Build locally: `kubectl kustomize deploy/overlays/oregon`.

## Node placement contract (must match terraform user-data labels/taints)

| Workload | placement | set by |
|----------|-----------|--------|
| Spring DaemonSet | `nodeSelector: role=app` | app ASG user-data `--node-label role=app` |
| Redis + Prometheus | `nodeSelector: role=data` | data node user-data `--node-label role=data` |
| Traefik Gateway | ingress node (`role=ingress`) | ingress user-data `--node-label role=ingress` |
| ArgoCD core | CP node, tolerates `node-role.kubernetes.io/control-plane=true:NoSchedule` | CP user-data `--node-taint` |

## Image tag flow (CI arm64 → ECR → tag bump)

`overlays/oregon/kustomization.yaml` holds the `images:` transform for `danteplanner-backend`.
`.github/workflows/deploy-oregon.yml` builds the arm64 image, pushes it to ECR, then runs
`kustomize edit set image danteplanner-backend=<ecr-host>/danteplanner-backend:<sha>` and commits
the bump back. ArgoCD sees the commit and rolls the DaemonSet.

## Deviations (documented)

- **ESO no-IRSA:** `external-secret.yaml`'s SecretStore has no `auth` block, so the AWS SDK
  default chain uses the **node instance profile** (the app node role granted
  `secretsmanager:GetSecretValue` in terraform/oregon). Coarser than IRSA — any pod on that node
  inherits the grant. Accepted deviation.
- **No autonomous writer promotion:** the auth Redis StatefulSet runs a single primary with no
  Sentinel/auto-failover; an outage is a typed 503 + node auto-recovery + AOF replay.

## Platform prerequisites (installed unattended by the CP bootstrap)

The CP user-data (`terraform/oregon/user-data/cp.sh.tftpl`) installs these before ArgoCD syncs
the overlay, because the API server rejects a custom resource whose CRD is absent:

- **Gateway API CRDs** (`gateway_api_version`) — k3s ships none and the bundled Traefik is
  disabled; the authored `Gateway`/`HTTPRoute`/`GatewayClass` need these kinds.
- **External Secrets Operator** (`external_secrets_chart_version`, Helm) — CRDs + controller,
  pinned `nodeSelector.role=app` so its credential resolves to the app node instance profile
  (the role granted `secretsmanager:GetSecretValue`). This is what makes the ESO no-IRSA
  boundary work.

## Operator-supplied at apply time

- `origin-tls` Secret (Cloudflare Authenticated Origin Pull cert + client CA) referenced by the
  Gateway — populate before serving traffic.
- `overlays/oregon/configmap-patch.yaml` `MYSQL_HOST` = the terraform/rds `rds_endpoint` output.
- The JWT material must exist in Secrets Manager: `danteplanner/jwt/rs256-private-key`
  (private key PEM), `danteplanner/jwt/rs256-public-key` (X.509 public key PEM), and
  `danteplanner/jwt/encryption-key` (base64 AES-256 string). All three are required — the
  backend fails fast at boot without them.
- Grafana Cloud remote-write config (two datasources) is layered in the overlay when wired.
