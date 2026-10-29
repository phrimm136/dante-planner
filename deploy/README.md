# deploy/ — GitOps manifests (kustomize base + overlays)

One repo, kustomize `base/` + `overlays/{oregon,seoul}`, synced by ArgoCD core (per cluster).
Each region's CP node bootstraps ArgoCD, applies `argocd/project.yaml`, then applies its root
Application: `argocd/root-app-oregon.yaml` points ArgoCD at `overlays/oregon`, and
`argocd/root-app-seoul.yaml` at `overlays/seoul`. From then on the cluster is GitOps-managed.

## Layout

```
deploy/
  argocd/project.yaml          AppProject default (ArgoCD core ships none; applied before the roots)
  argocd/root-app-oregon.yaml  ArgoCD Application → overlays/oregon (app-of-apps root)
  argocd/root-app-seoul.yaml   ArgoCD Application → overlays/seoul (app-of-apps root)
  base/                    region-invariant manifests, all in namespace danteplanner
    spring-daemonset.yaml  Spring backend, DaemonSet on role=app nodes
    spring-service.yaml    backend Service + ConfigMap
    traefik-gateway.yaml   Gateway API: Gateway + app HTTPRoute + /healthz-local route
    external-secret.yaml   ESO SecretStore + ExternalSecret (RS256 private key ← Secrets Manager)
    jwt-secrets.yaml       ExternalSecrets: RS256 public key + AES-256 encryption key ← Secrets Manager
    redis-auth.yaml        auth-state Redis StatefulSet on role=data (AOF, no auto-promote)
    redis-ratelimit.yaml   ephemeral rate-limit Redis on role=data (no durability)
    prometheus.yaml        Prometheus on role=data (local scrape)
  privileged-exporter/     namespace privileged-exporter (enforce: privileged) + node-exporter DaemonSet
  overlays/oregon/         region-specific values (image, endpoints); lists base/ and privileged-exporter/
  overlays/seoul/          the same for Seoul
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

`overlays/oregon/kustomization.yaml` and `overlays/seoul/kustomization.yaml` hold the `images:`
transform for `danteplanner-backend`. `.github/workflows/deploy-fleet.yml` runs after PR Gate
passes on `main`, or when you dispatch it by hand. It builds the arm64 image and pushes it to the
Oregon and Seoul ECR registries. It scales each region's app ASG to two nodes. Then it runs
`kustomize edit set image danteplanner-backend:<sha>` in both overlays and commits the new tag
back. The ArgoCD core in each region sees the commit and rolls the DaemonSet. The workflow then
waits for both rollouts and scales the app ASGs back to one node.

Git holds only the tag. The CP user-data (`terraform/modules/fleet/user-data/cp.sh.tftpl`)
patches each root Application with a kustomize image override that adds the ECR registry host.

## Pod Security Admission labels

`base/namespace.yaml` labels the `danteplanner` namespace with the `restricted` Pod Security
Standard in `warn` and `audit` mode. There is no `enforce` label. The API server admits every pod
as before. In `warn` mode it returns a warning to the client that applies a pod or a workload
whose pod template breaks the profile. The k3s API server in this repo has no audit log
configured, so the `audit` label records nothing today. Do not add `enforce` while two workloads
in `danteplanner` break the `baseline` profile too. `traefik` uses `hostNetwork` and `hostPort`
443. `cloudflared` uses `hostNetwork`. Add `enforce: restricted` only after both stop using
`hostNetwork` and `traefik` stops using `hostPort`. The staging rehearsal manifests in
`staging-e2e/` are applied into `danteplanner` by hand, so they must pass `restricted` too; their
`securityContext` settings follow the base workloads.

`node-exporter` needs `hostNetwork`, `hostPID` and `hostPath` volumes to read node metrics, so it
does not run in `danteplanner`. `privileged-exporter/namespace.yaml` puts it in its own namespace,
`privileged-exporter`, with `enforce: privileged`. Each overlay lists `privileged-exporter/` next
to `base/`. Put a workload in `privileged-exporter` only if it needs the host.

## Deviations (documented)

- **ESO no-IRSA:** `external-secret.yaml`'s SecretStore has no `auth` block, so the AWS SDK
  default chain uses the **node instance profile** (the app node role granted
  `secretsmanager:GetSecretValue` in terraform/oregon). Coarser than IRSA — any pod on that node
  inherits the grant. Accepted deviation.
- **No autonomous writer promotion:** the auth Redis StatefulSet runs a single primary with no
  Sentinel/auto-failover; an outage is a typed 503 + node auto-recovery + AOF replay.

## Platform prerequisites (installed unattended by the CP bootstrap)

The CP user-data (`terraform/modules/fleet/user-data/cp.sh.tftpl`) installs these before ArgoCD syncs
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
