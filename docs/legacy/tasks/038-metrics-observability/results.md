# Results — 038 Amendment: Kubernetes Object-State Metrics, Coverage Expansion & Alert Delivery

## What Was Done

All 11 planned phases built and committed, plus a substantial post-build live-wiring effort that
the spec scoped as "step-3 territory" but which was pulled forward in the same session:

- Prometheus prerequisites: per-region `cluster` external label, generic `prometheus.io/job`
  relabel, self-scrape job (phase 01)
- kube-state-metrics per cluster with a nine-entry allowlist including the etcd-snapshot CRS
  metric (phase 02)
- mysqld_exporter per region (phase 03), later fixed for TLS (`require_secure_transport`) and the
  Seoul replica's certificate hostname
- Gap-cluster scrape wiring: ArgoCD/ESO/CoreDNS annotations + dedicated apiserver/etcd scrape
  jobs + `--etcd-expose-metrics` (phase 04, scope expanded by option-A ruling)
- Deploy-marker workflow step (phase 05)
- Handoff amendment incl. dev→main correction (phase 06)
- Alert delivery: `alert-dual-channel` contact point + Default policy; six Grafana-managed rules
  + Seoul replica CW-datasource rule created live via the provisioning API (phases 07–09)
- Live verification runbooks (phases 10–11), later executed: mysqld credentials provisioned, RDS
  monitoring user created, INV7 baselines captured
- Beyond the spec: remote_write wiring with a keep-list + recording rules + 60s scrape (fits the
  Grafana Cloud 10k free tier), config-reloader sidecar, node_exporter (§E pulled forward),
  redis_exporter (§D pulled forward), Alloy→Loki log pipeline, a repo-persisted Grafana
  provisioning kit (`deploy/grafana/`), ops scripts (`scripts/ops/provision-*`, `rds-memory-gate`,
  `fleet-metrics-verify`), two SSM State Manager convergence associations, IAM-as-code adoption
  of the CloudWatch datasource user, `log_slow_extra` + query-pathology counters, and a
  33-panel fleet dashboard

## Files Changed

- `deploy/base/`: prometheus.yaml (labels, relabel, jobs, remote_write, rules.yml, reloader
  sidecar), kube-state-metrics.yaml, mysqld-exporter.yaml (+TLS cnf/CA bundle generators),
  redis-exporter.yaml, node-exporter.yaml, alloy-logs.yaml, grafana-remote-write-secret.yaml,
  kustomization.yaml
- `deploy/overlays/{oregon,seoul}/`: cluster patches for prometheus and alloy, mysqld endpoint
  patches, kustomizations
- `deploy/grafana/`: fleet-dashboard.json + import/drill/staleness provisioning scripts
- `terraform/modules/fleet/`: cp.sh.tftpl (annotations converger, etcd flag),
  scrape-annotate-install.sh, ssm-annotate.tf, ssm-etcd-metrics.tf, cp.tf
- `terraform/rds/main.tf`: log_slow_extra (performance_schema added then reverted)
- `terraform/secrets/grafana-datasource-iam.tf`: IAM user + policy adoption via import blocks
- `.github/workflows/`: deploy-fleet.yml (deploy marker), verify-rds-ca.yml
- `.githooks/pre-commit`: private-key PEM guard
- `backend/src/main/resources/application.properties`: percentile histograms
- `scripts/ops/`: provision-grafana-{metrics,logs}-secrets.sh, provision-mysqld-secrets.sh,
  rds-memory-gate.sh, fleet-metrics-verify.sh
- `docs/tasks/038-metrics-observability/`: handoff amendment, phase ledgers/verifications,
  manifest, task verification

## Verification

- Build: both overlays render clean (`kubectl kustomize`); rendered Prometheus config + recording
  rules pass `promtool check` (v2.54.1 via docker); terraform stacks validate
- Task-level verification: PASS (`verification.md`) — all render assertions, INV11 repo-wide grep
  clean, cross-phase invariants verified
- Final review: ACCEPTABLE (5/5 reviewers; 1 Medium deferred by design, rest Low/cosmetic)
- Live (manually confirmed): remote_write flowing both regions with `cluster` labels
  distinguishing streams (INV2 observed); `mysql_up 1` in Oregon post-TLS-fix (Seoul fix
  committed, pending merge); six alert rules + rule S live; `argocd-app-drift` alert fired on a
  real Degraded state and resolved after the fix (first genuine catch); scrape-annotate and
  etcd-metrics SSM associations both `Success` in both regions
- Outstanding live items are listed in session-state.md

## Issues & Resolutions

- KSM + mysqld scraped at the inherited `/actuator/prometheus` default → 404, discovered-but-down
  for days behind `count(up)` → `prometheus.io/path: /metrics` on both; lesson: count(up) counts
  targets, not health
- mysqld Error 3159 (RDS `require_secure_transport`) → CA bundle + my.cnf ssl-ca via generated
  ConfigMaps; interactive clients negotiate TLS, Go exporters don't
- Seoul x509 hostname mismatch (cert names the RDS endpoint, not the Route53 alias) → replica
  endpoint via ExternalSecret; never skip-verify
- Grafana Cloud 69k→79k active series vs 10k free tier → write_relabel keep-list + recording
  rules (per-endpoint aggregation) + 60s scrape; count decays over ~30min
- k3s addon controller reverts CoreDNS patches on every server start → systemd oneshot converger
  (WantedBy=k3s.service) delivered by SSM State Manager association; etcd enable association keys
  on observable state (`curl :2381`), not file state
- Merged Prometheus config sat unread (process loads config at startup; restart wipes emptyDir
  TSDB) → config-reloader sidecar + `--web.enable-lifecycle`
- Evidence-destroying scripts (curl -f, `jq // "0"`, silent CW empty results) repeatedly reported
  permission failures as data absence → status+body capture pattern adopted kit-wide
- SSM `--parameters` shorthand metacharacters (documented in the handoff, still hit) → jq-built
  JSON form
- Alloy river rejects `username_file` (Prometheus idiom not adopted) → env-fed username +
  password_file
- performance_schema OFF by default; FreeableMemory baselines 122/128 MB on 1GiB → enable
  REVERTED pre-reboot by the memory gate; `log_slow_extra` (dynamic) + global-status pathology
  counters recovered most of the digest value

## Learnings

- The phase pipeline's render gates verify config text, not runtime behavior; the entire
  post-build marathon was runtime truth diverging from rendered truth. Live-verify phases earn
  their keep only when actually executed.
- Verification checks must be one-sided-error: prefer probes that can fail loudly
  (targets API lastError) over counts that look green in every state.
- Every filter/allowlist creates a silent two-place contract (exists AND admitted); pair each
  with an absence alarm or a coverage check.
- Provisioning choreography (secrets before merge; terraform reads local disk, ArgoCD reads
  merged main) is worth stating once per pipeline, not rediscovering per incident.
- User-demand-driven scope pull-forward (dashboards, logs, node_exporter) went fine BECAUSE the
  earlier phases had established patterns (ESO flow, keep-list, converger) to copy.

## Spec Divergence

### What Changed
- `mechanics.md §1` "eight-metric allowlist" → nine entries: `--metric-allowlist` also filters
  custom-resource-state metrics, so the etcd CRS metric had to join the list or emit nothing.
- `mechanics.md §3` "two contact points (Discord primary / Slack fallback)" → ONE contact point
  with two integrations on the Default policy: a lone match-all nested route redirects instead of
  fanning out, delivering one channel only; the single-point/two-integration form is the
  trap-free realization of INV6.
- `plan phase-04` file scope → expanded (option-A ruling) to `deploy/base/prometheus.yaml` +
  the k3s flag: apiserver/etcd run in-process on the CP, unreachable by pod-annotation discovery.
- `mechanics.md §4` "idempotent — apply once manually to live CPs, converges on rebuild" (ArgoCD/
  ESO/CoreDNS annotations) → wrong for CoreDNS (see Wrong Assumptions); replaced by a
  k3s-start-triggered converger + State Manager delivery.
- `mechanics.md §6` perf_schema scope "digest + table_io instruments only" → deferred entirely:
  the INV7 gate predicted failure before any reboot (122/128 MB free on 1GiB). Digest-class
  questions rerouted to `log_slow_extra` slow-log fields + global-status pathology counters.
- Alert rule M and the rule-evaluation drills were spec-deferred to step-3 remote_write — but
  remote_write itself was then built in-session, so rule M is live and the drills are unblocked
  rather than deferred.

### What Was Added (Not in Spec)
- Remote_write + keep-list + recording rules + 60s scrape (step-3 pulled forward under a live
  free-tier ceiling; the budget crisis was not foreseeable until real series counts existed)
- Alloy→Loki log pipeline + WARN/ERROR list panel (logs were never selected in the handoff; the
  operator's CW-dashboard parity expectation surfaced only when the Grafana dashboard existed)
- node_exporter and redis_exporter (handoff step-2, pulled forward by operator demand)
- The 33-panel fleet dashboard + repo-persisted provisioning kit (spec explicitly scoped
  dashboards out; operational reality scoped them back in)
- Config-reloader sidecar; SSM State Manager convergence pattern (×2); IAM-as-code adoption;
  CA-bundle drift CI; private-key pre-commit hook; log_slow_extra
- Percentile histograms property (handoff step-1 item that had never shipped)

### What Was Dropped
- performance_schema digests — deferred with a recorded revisit trigger (instance upsize);
  decision drafted to meme
- Cert-expiry and clock-skew alerts — deferred to step-2 exporters per the recorded spec ruling
  (blackbox not yet deployed; node_exporter now IS deployed, so the clock-skew rider is
  unblocked earlier than expected)
- EXPLAIN ANALYZE as a recorded metric (spec already dropped it; confirmed as on-demand drill)

### Wrong Assumptions
- "k3s CoreDNS annotations converge on rebuild and persist otherwise" → k3s's addon controller
  rewrites and re-applies bundled manifests on EVERY server start, reverting patches; persistence
  requires a converger on the same trigger.
- "Annotation contract = scrape/port/job" (implicit in mechanics §1/§4 tables) → the PATH
  annotation is load-bearing: pods without it inherit the backend job's `/actuator/prometheus`
  default and 404 forever.
- "The replica may be dialed via its Route53 private-zone alias" (deploy/CLAUDE.md committability
  rule applied to the exporter) → TLS hostname verification rejects the alias; the certified
  name must be dialed, delivered as a secret.
- "mysqld_exporter connects like the mysql client" → server-side transport enforcement rejects
  plaintext drivers that interactive clients transparently negotiate around.
- "Grafana Cloud free tier will absorb the fleet's series" (unexamined) → full-fidelity
  forwarding costs 7–8× the ceiling; budgeting is a first-class design input.

### Prompting Retrospective
- **Runtime-vs-render verification**: "For each scrape target the spec adds, what single live
  query proves it is being scraped successfully — and which alert fires when it is not?"
  - Would have surfaced the path-annotation contract and the absent(up) dead-man priority before
    two blind days.
- **Cost ceilings**: "Multiply out the expected active-series count per feature (buckets × routes
  × pods × clusters) against the plan's limit before selecting metrics."
  - The series budget existed in the spec as INV3 (~2k for KSM) but no whole-pipe budget existed.
- **Managed-platform reverters**: "For every object we patch, which controller also writes it,
  and on what trigger?"
  - Would have caught the k3s addon controller before the manual-patch round.
- **TLS posture inheritance**: "The DB enforces require_secure_transport — list every NEW client
  this task adds and how each one satisfies TLS, including hostname verification through any
  DNS aliases."
- **Operator-experience parity**: "Which views from the existing CW ops dashboard must exist in
  Grafana on day one?" — dashboards were scoped out as step-3, but the operator's first act was
  to look for them.

### Spec Process Takeaway
This spec systematically missed **runtime contracts of the platforms it composed** — the
annotation schema's failure default, the addon controller's reconciliation, TLS transport
policy, the billing model — i.e., constraints owned by the surrounding systems rather than by
the code being written; a "for each platform we touch, what does it do to our objects without
asking" pass would have caught four of the five wrong assumptions.
