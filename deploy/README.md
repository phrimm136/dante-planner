# deploy/ — GitOps manifests (one Helm chart + Helm Applications)

One repo, one Helm chart (`chart/`) with `values-{oregon,seoul}.yaml`, synced by ArgoCD core (per
cluster). Third-party components come from their upstream Helm charts instead: each is one ArgoCD
Application under `chart/templates/`, so the root Application creates the child Applications in
namespace `argocd` (app-of-apps).
Each region's CP node bootstraps ArgoCD, applies `argocd/project.yaml`, then applies its root
Application: `argocd/root-app-oregon.yaml` points ArgoCD at `chart/` with `values-oregon.yaml`, and
`argocd/root-app-seoul.yaml` with `values-seoul.yaml`. From then on the cluster is GitOps-managed.

## Layout

```
deploy/
  argocd/project.yaml          AppProject default (ArgoCD core ships none; applied before the roots)
  argocd/root-app-oregon.yaml  ArgoCD Application → chart/ + values-oregon.yaml (app-of-apps root)
  argocd/root-app-seoul.yaml   ArgoCD Application → chart/ + values-seoul.yaml (app-of-apps root)
  chart/templates/         every manifest, in namespace danteplanner unless it names another
    spring-daemonset.yaml  Spring backend, DaemonSet on role=app nodes
    spring-service.yaml    backend Service + ConfigMap
    traefik-gateway.yaml   Gateway API: Gateway + app HTTPRoute (/api, /robots.txt) + /healthz-local route
    external-secret.yaml   ESO SecretStore + ExternalSecret (RS256 private key ← Secrets Manager)
    jwt-secrets.yaml       ExternalSecrets: RS256 public key + AES-256 encryption key ← Secrets Manager
    redis-auth.yaml        auth-state Redis StatefulSet on role=data (AOF, no auto-promote)
    redis-ratelimit.yaml   ephemeral rate-limit Redis on role=data (no durability)
    prometheus.yaml        ConfigMap with the scrape config, rules and remote write, the ClusterRole, and the ArgoCD Application (namespace argocd) that installs the prometheus chart on role=data
    cluster-identity.yaml  ConfigMap that holds the region name as CLUSTER_NAME, for charts (their Applications are identical in both regions)
    kube-state-metrics.yaml  ArgoCD Application (namespace argocd) that installs the kube-state-metrics chart on role=data, scraped locally by Prometheus
    mysqld-exporter.yaml   ArgoCD Application of the mysqld-exporter chart on role=data + the ExternalSecret that gives it the region's endpoint under one key
    mysqld-exporter-config.yaml  ConfigMap with the RDS CA bundle that mysqld-exporter mounts
    redis-exporter.yaml    ArgoCD Applications of the redis-exporter chart, one per Redis, on role=data
    alloy-logs.yaml        ArgoCD Application `alloy` (namespace argocd) that installs the Alloy chart on role=data, and the config and Loki credentials it reads; its config reloader applies a changed config without a restart
    external-secrets.yaml  ArgoCD Application (namespace argocd) that installs the External Secrets chart in namespace external-secrets on role=app
    privileged-exporter-namespace.yaml, node-exporter.yaml  namespace privileged-exporter (enforce: privileged) + ArgoCD Application of the node-exporter chart, a DaemonSet on every node
    aws-ebs-csi-driver.yaml  ArgoCD Application of the aws-ebs-csi-driver chart in kube-system, controller and node plugin on role=data
    storageclass-ebs.yaml  StorageClass ebs-gp3-retain: encrypted gp3 EBS volumes, kept on PVC deletion (Retain); not the default class
  chart/values.yaml        values common to both regions
  chart/values-oregon.yaml region-specific values (image tag, endpoints)
  chart/values-seoul.yaml  the same for Seoul
  chart/values.schema.json what a values file must hold, per region
```

Build locally: `helm template danteplanner-oregon deploy/chart -f deploy/chart/values-oregon.yaml`.
That render holds each upstream chart only as its Application;
`.github/scripts/render-charts.sh <out-dir> <rendered.yaml>` renders the charts themselves with
`helm template --include-crds`, as the deploy lane and the config scan do. ArgoCD includes a chart's
`crds/` directory too.

## CRDs

ArgoCD prunes what a chart stops rendering, and deleting a CRD deletes every object of its kind. (The
removal of a whole Application leaves its CRDs in the cluster.) The deploy
lane's `charts` stage runs `.github/scripts/rendered-crds.py` over both region renders and all chart
renders: a custom kind in use must have its CRD rendered, in a version that CRD serves, or its group
must be in `OUTSIDE_GROUPS` in that script, the groups whose CRDs something other than an
Application installs. So a CRD that leaves while its objects stay fails, and a CRD and its
objects leaving in one merge pass. An entry whose CRD is rendered, or whose group no rendered object
uses, fails.

## Chart versions

An Application pins its chart in `spec.source.targetRevision` and carries its values in
`spec.source.helm.valuesObject`; the release name equals the Application name, because ArgoCD
tracks resources by the `app.kubernetes.io/instance` label the chart sets to the release name.
Renovate (`renovate.json`) proposes a newer chart version once it is seven days old. No CI lane
reads `renovate.json`: Renovate's hosted app reports an invalid file by opening an issue in the
repository. A values change or a version bump lands by merging it: ArgoCD syncs the Application
from `main`.

Helm ignores a value that a chart raise renamed, so the `charts` stage also runs
`.github/scripts/rendered-exporters.py` over the region and chart renders. It fails when an exporter's scrape
annotations or `app` label, the args of node-exporter or mysqld-exporter, mysqld-exporter's
endpoint, credentials, my.cnf or CA mount, the Secret keys and CA file behind them, or a Redis
exporter's args or env are no longer rendered as the metrics need them.

External Secrets is the exception: Renovate does not raise it (`enabled: false` for
`external-secrets` in `renovate.json`), because the CP boot script installs the same chart and the
version of the Application must equal the `external_secrets_chart_version` defaults in
`terraform/modules/fleet`, `terraform/oregon` and `terraform/seoul`.
`scripts/ops/tests/test_external_secrets_application.py` fails when they differ. Raise all of them in one
change, one minor version at a time.

The values of the External Secrets Application are the flags of the boot script plus the scrape
annotations that `/usr/local/bin/scrape-annotate.sh` writes on the controller's pod template; the
same test fails when they differ. Its live objects carry `app.kubernetes.io/instance: external-secrets`,
so ArgoCD counts them as the Application's own. Its finalizer deletes the operator, but not its CRDs,
when the Application is removed from the chart. To stop managing the operator and keep it, remove the
finalizer in one merge, wait until both regions have synced it, and remove the Application in the next.

The `render` stage runs `.github/scripts/rendered-redis-probes.py` over the region renders: it fails
when redis-auth or redis-ratelimit loses its TCP liveness probe or its readiness probe that passes
only when PING answers PONG, which keeps a Redis replaying its AOF out of its Service.

The `behavior` stage serves the rendered HTTPRoutes through Traefik to a header-echo backend.
Every rule that targets the backend needs a row in `.github/deploy-test/route-contract.tsv`, whose
probe must reach the backend with that row's path and headers. Each path in
`.github/deploy-test/unrouted-paths.txt` must match no rendered backend rule and must get 404 from
Traefik; these are the paths the public edge must not forward, `/actuator/**` among them. Only the
listed paths are checked: a new rule that serves an unlisted path such as another actuator
endpoint passes unless a path in that file falls under it.

Renovate also raises the pins of the CI scripts:

- The k3s release, in one pull request (group `k3s`) that opens only after it is approved on the
  dependency dashboard: the `k3s_version` default in `terraform/modules/fleet/variables.tf`,
  which the nodes install; `K3S_IMAGE` with its digest in `.github/scripts/deploy-test.sh`, which
  the deploy lane boots and whose tag also sets the Kubernetes version the schemas are checked
  against; and the `KUBERNETES_SCHEMAS` commit, raised to the tip of yannh/kubernetes-json-schema
  `master`. The lane's `schema` stage fails when the k3s default and the `K3S_IMAGE` tag name
  different releases, or when the schema commit has no directory for that Kubernetes version.
- Every other `<NAME>_IMAGE=<image>:<tag>@sha256:<digest>` line in `.github/scripts/images.sh`
  and `.github/scripts/deploy-test.sh` (Checkov, Helm, kubeconform, whoami,
  Prometheus, Loki), tag and digest together, one pull request per image, with no approval step.
  A Checkov raise can add findings, and the scan baseline only shrinks: when `scan-config` fails
  on a Checkov raise with NEW lines, that pull request fixes each finding or records it with a
  `checkov.io/skip1` annotation or a `checkov:skip` comment before it merges.

Renovate often has no release date for an image tag and never has one for a commit; such an
update is proposed without the seven-day wait, while one with a date still waits.
The Helm image stays on major 3 (`allowedVersions` in `renovate.json`), the major of the Helm
that ArgoCD renders with; raise the limit together with an ArgoCD release that ships a newer major.
`GATEWAY_API_CRDS` stays pinned by hand: its commit follows the
Gateway API release the cluster installs (`gateway_api_version`), not the tip of a branch.

Moving kube-state-metrics to its chart changed one behavior: the chart's ClusterRole also lets
it list and watch CustomResourceDefinitions, which the etcd snapshot metric
(`kube_etcd_snapshot_creation_timestamp_seconds`) needs and the hand-written role lacked. The
metric has one series per retained snapshot, so the `etcd-snapshot-deadman` rule reads the
newest one per cluster. It fires when that one is older than 1.5 times the snapshot interval
(`etcd_snapshot_interval_hours` in `terraform/modules/fleet/variables.tf`, which
`deploy/grafana/import-alert-rules.sh` reads), and when a cluster's kube-state-metrics is up
but serves no snapshot series. Its `for: 10m` covers a kube-state-metrics restart, whose first
scrapes can come before the custom-resource series appear.

## Node placement contract (must match terraform user-data labels/taints)

| Workload | placement | set by |
|----------|-----------|--------|
| Spring DaemonSet | `nodeSelector: role=app` | app ASG user-data `--node-label role=app` |
| Redis + Prometheus + kube-state-metrics + Alloy + EBS CSI driver | `nodeSelector: role=data` | data node user-data `--node-label role=data` |
| Traefik Gateway | ingress node (`role=ingress`) | ingress user-data `--node-label role=ingress` |
| ArgoCD core | CP node, tolerates `node-role.kubernetes.io/control-plane=true:NoSchedule` | CP user-data `--node-taint` |

## EBS volumes

`aws-ebs-csi-driver.yaml` installs the AWS EBS CSI driver, which creates an EBS volume for a
PersistentVolumeClaim of class `ebs-gp3-retain` and attaches it to the node the pod runs on. Its
controller and its node plugin run only on the data node, the one node with stateful workloads,
and call the EC2 API with the data node's instance profile. Both take the region from the
instance metadata service, so the Application is identical in both regions. The driver tags
every volume it creates `ebs.csi.aws.com/cluster=true`. `local-path` stays the
default StorageClass, so only a claim that names `ebs-gp3-retain` gets an EBS volume. The node
plugin is privileged: kube-system carries no Pod Security label and the k3s API server has no
admission config, so it is admitted. The chart adds its snapshotter only where the
`snapshot.storage.k8s.io` CRDs exist, and nothing in this repository installs them. The chart's
Checkov findings are recorded as `checkov.io/skip<n>` annotations on its Deployment and DaemonSet.

## Image tag flow (CI arm64 → ECR → tag bump)

`chart/values-oregon.yaml` and `chart/values-seoul.yaml` hold `image.tag` for
`danteplanner-backend`. `.github/workflows/deploy-fleet.yml` runs after PR Gate
passes on `main`, or when you dispatch it by hand. If the merge did not change an image input
since the deployed tag, the whole chain skips and nothing deploys. The input list lives in
`.github/scripts/deploy-inputs-changed.sh`, and a PR Gate step fails when the Dockerfile copies a
path the list does not cover. A manual dispatch always deploys. Otherwise the workflow builds the
arm64 image and pushes it to the Oregon and Seoul ECR registries. It scales each region's app ASG
to two nodes. Then it writes the tag to both values files and commits the new tag
back. The ArgoCD core in each region sees the commit and rolls the
DaemonSet. The workflow then waits for both rollouts and scales the app ASGs back to one node.

Git holds only the tag. The CP user-data (`terraform/modules/fleet/user-data/cp.sh.tftpl`)
patches each root Application with the Helm parameter `image.repository`, which adds the ECR
registry host.

## Pod Security Admission labels

`chart/templates/namespace.yaml` labels the `danteplanner` namespace with the `restricted` Pod Security
Standard in `warn` and `audit` mode. There is no `enforce` label. The API server admits every pod
as before. In `warn` mode it returns a warning to the client that applies a pod or a workload
whose pod template breaks the profile. The k3s API server in this repo has no audit log
configured, so the `audit` label records nothing today. Do not add `enforce` while two workloads
in `danteplanner` break the `baseline` profile too. `traefik` uses `hostNetwork` and `hostPort`
443. `cloudflared` uses `hostNetwork`. Add `enforce: restricted` only after both stop using
`hostNetwork` and `traefik` stops using `hostPort`. The staging rehearsal manifests in
`staging-e2e/` are applied into `danteplanner` by hand, so they must pass `restricted` too; their
`securityContext` settings follow the chart's workloads.

`node-exporter` needs `hostNetwork`, `hostPID` and `hostPath` volumes to read node metrics, so it
does not run in `danteplanner`. `chart/templates/privileged-exporter-namespace.yaml` puts it in its own
namespace, `privileged-exporter`, with `enforce: privileged`. Put a workload in `privileged-exporter` only if it needs the host.

## Deviations (documented)

- **ESO no-IRSA:** `external-secret.yaml`'s SecretStore has no `auth` block, so the AWS SDK
  default chain uses the **node instance profile** (the app node role granted
  `secretsmanager:GetSecretValue` in terraform/oregon). Coarser than IRSA — any pod on that node
  inherits the grant. Accepted deviation.
- **No autonomous writer promotion:** the auth Redis StatefulSet runs a single primary with no
  Sentinel/auto-failover; an outage is a typed 503 + node auto-recovery + AOF replay.

## Platform prerequisites (installed unattended by the CP bootstrap)

The CP user-data (`terraform/modules/fleet/user-data/cp.sh.tftpl`) installs these before ArgoCD syncs
the chart, because the API server rejects a custom resource whose CRD is absent:

- **Gateway API CRDs** (`gateway_api_version`) — k3s ships none and the bundled Traefik is
  disabled; the authored `Gateway`/`HTTPRoute`/`GatewayClass` need these kinds.
- **External Secrets Operator** (`external_secrets_chart_version`, Helm) — CRDs + controller, the
  same chart and release that `chart/templates/external-secrets.yaml` then manages,
  pinned `nodeSelector.role=app` so its credential resolves to the app node instance profile
  (the role granted `secretsmanager:GetSecretValue`). This is what makes the ESO no-IRSA
  boundary work.

## Operator-supplied at apply time

- `origin-tls` Secret (Cloudflare Authenticated Origin Pull cert) referenced by the
  Gateway — populate before serving traffic.
- `MYSQL_HOST` in the runtime-config bundle (Secrets Manager) = the terraform/rds `rds_endpoint` output.
- The JWT material must exist in Secrets Manager as the `danteplanner/jwt/material` JSON
  bundle with the keys `rs256_private_key` (private key PEM), `rs256_public_key` (X.509 public
  key PEM), and `encryption_key` (base64 AES-256 string). All three are required — the
  backend fails fast at boot without them.
- Grafana Cloud remote-write credentials (`chart/templates/grafana-remote-write-secret.yaml`).
