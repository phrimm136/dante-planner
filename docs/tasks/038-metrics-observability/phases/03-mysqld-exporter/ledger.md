# Phase 03 Scenario Ledger: mysqld_exporter manifest

Dossiers: $HOME/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-03/
Baseline: HEAD 95774978 (phases 01+02 committed; manifest.json now tracked). Pre-existing dirty tree
  (NOT this phase's to stage): tracked M on .claude/*, deploy/base/spring-daemonset.yaml, scripts/ops/lib/*,
  docs/tasks/038-.../requirements.md, static (submodule); untracked task scaffolding
  docs/tasks/038-.../plan/; many unrelated ?? task dirs. Phase-03 files clean at baseline (delta surfaces
  them): deploy/base/mysqld-exporter.yaml (new), deploy/overlays/{oregon,seoul}/mysqld-endpoint-patch.yaml
  (new), deploy/base/kustomization.yaml + both overlay kustomizations (M-to-come, additive registration),
  manifest.json (M-to-come). Phase set = current porcelain minus this baseline.
Runner: `kubectl kustomize deploy/overlays/{oregon,seoul}` (kustomize binary absent; kubectl is fallback,
  verified phases 01-02).

## Research-pinned facts (web-research-specialist, sourced — GitHub source/releases)
- Image: `prom/mysqld-exporter:v0.19.0` (Docker Hub `prom/`, matches deploy/base/prometheus.yaml
  `prom/prometheus:v2.54.1` registry convention; quay.io/prometheus mirror is equivalent). v0.19.0 = latest
  stable (2026-03-18).
- Credential/host delivery (v0.15+ REMOVED `DATA_SOURCE_NAME` — any legacy DSN-env manifest silently
  no-ops to localhost): host via flag `--mysqld.address=<host:port>` (default localhost:3306); username via
  flag `--mysqld.username`; password via env `MYSQLD_EXPORTER_PASSWORD`. `--config.my-cnf` is OPTIONAL
  (Loose load; missing file tolerated) — pure flags+env path avoids the #672/#840 "[client] section" class.
  k8s $(VAR) substitution in args works: `--mysqld.username=$(MYSQL_EXPORTER_USER)` resolves from container
  env. Host can be an env too: `--mysqld.address=$(MYSQLD_ADDRESS)`.
- perf_schema collectors ALL default to `false` — opt in ONLY. Digest = `--collect.perf_schema.eventsstatements`
  (per-digest text, matches handoff:100-115 "per-digest top-N"; NOT the cheaper `eventsstatementssum` aggregate).
  Table I/O = `--collect.perf_schema.tableiowaits`. Because all perf_schema default off, "ONLY these two"
  needs NO `--no-collect`; the negative assertion is simply "no other `--collect.perf_schema.*` present."
- Default-ENABLED collectors: `global_status`, `global_variables`, `slave_status` — LEAVE AT DEFAULT.
  Do NOT `--no-collect.slave_status`: it is not a perf_schema instrument (no memory-gate effect), the monitoring
  user's REPLICATION CLIENT grant (mechanics §6) exists FOR it, and disabling blinds Seoul replica-lag —
  unrequested scope (CRITICAL RULE #1). Advisor-adjudicated.
- Metrics port default `--web.listen-address :9104`.

## Settled Decisions (every later Brief restates the relevant ones as fact)
- D1 IMAGE: `prom/mysqld-exporter:v0.19.0`.
- D2 SCRAPE ANNOTATIONS (phase-02 ratified UNQUOTED-annotation convention carried in): pod-template
  annotations `prometheus.io/scrape: "true"`, `prometheus.io/port: "9104"`, `prometheus.io/job: mysqld`
  (job value UNQUOTED — kubectl serializes plain-word annotation values unquoted; a quoted `"mysqld"`
  assertion is perpetual-red).
- D3 PLACEMENT: Deployment `nodeSelector role: data`, `replicas: 1` (one per region — base manifest is
  region-identical; per-region difference is ONLY the endpoint, via overlay patch).
- D4 CREDENTIAL DELIVERY (INV11 local half): reuse the EXISTING SecretStore `aws-secrets-manager`
  (deploy/base/external-secret.yaml; Seoul's secretstore-region-patch.yaml already retargets it to
  ap-northeast-2) — do NOT author a new SecretStore. Add ONE ExternalSecret producing k8s Secret
  **`mysqld-exporter-dsn`** with secretKeys **`username`, `password`, `primary-endpoint`**. Deployment
  consumes: `--mysqld.username=$(MYSQL_EXPORTER_USER)` where env `MYSQL_EXPORTER_USER` ← secretKeyRef
  (mysqld-exporter-dsn/username); env `MYSQLD_EXPORTER_PASSWORD` ← secretKeyRef(mysqld-exporter-dsn/password).
  NO literal credential (password/user/DSN) anywhere in any manifest.
- D5 ENDPOINT INDIRECTION + REGION-CORRECTNESS (spec §6 read-local; advisor-reconciled against repo
  evidence): base arg `--mysqld.address=$(MYSQLD_ADDRESS)`; env `MYSQLD_ADDRESS` set PER-REGION by the
  overlay endpoint patch (mirrors prometheus-cluster-patch's per-region env pattern; avoids kustomize
  args-list merge). ASYMMETRIC by repo convention:
  - Oregon (primary): env `MYSQLD_ADDRESS` `valueFrom.secretKeyRef` (mysqld-exporter-dsn/`primary-endpoint`).
    The Oregon primary RDS endpoint is NOT committable (no Route53 record exists; app keeps MYSQL_HOST in
    the Secrets Manager bundle out of committed overlays — deploy/overlays/oregon/configmap-patch.yaml) →
    render carries a secretKeyRef, NO literal host.
  - Seoul (replica): env `MYSQLD_ADDRESS` literal `value: mysql-replica.seoul.danteplanner.internal:3306`
    — the sanctioned committable Route53 private-zone name, EXACTLY as the app's existing
    `MYSQL_REPLICA_HOST` in deploy/overlays/seoul/configmap-patch.yaml (byte-copy bundle carries only the
    primary; the replica must be wired at the region overlay surface).
  Render vacuity: Oregon render carries the secretKeyRef and NOT `mysql-replica.seoul`; Seoul render carries
  the literal `mysql-replica.seoul.danteplanner.internal`.
- D6 perf_schema: exactly `--collect.perf_schema.eventsstatements` + `--collect.perf_schema.tableiowaits`;
  NO other `--collect.perf_schema.*`; NO `--no-collect.*` added.
- D7 TEST MODEL (phases 01-02 ratified): NO persisted render-test harness exists — do NOT invent one
  (CRITICAL RULE #1). Greenfield: a single baseline render (all contract tokens 0/0) is the ASSERTION-RED
  proof for ALL scenarios (tokens additive). Each green gate re-renders both overlays and confirms this
  scenario's tokens present + prior scenarios' tokens intact + LATER scenarios' tokens still absent
  (minimum-code guard). Structural facts not greppable (replicas/nodeSelector scoping) confirmed by
  python3 yaml parse of the rendered mysqld-exporter Deployment, as phase 01 did for prometheus.yml.

## Acceptance
- Test: both overlays `kubectl kustomize` render clean (exit 0) AND carry all mysqld_exporter contract
  tokens region-correctly (render-grep + structural parse; no persisted suite — D7).
- Contract token set (grepped 0/0 at baseline = RED; 1/1 region-correct at close = GREEN): image
  `prom/mysqld-exporter:v0.19.0`; nodeSelector `role: data` + replicas 1 (parse); annotations
  scrape/port:9104/job:mysqld(unquoted); ExternalSecret target `mysqld-exporter-dsn` on SecretStore
  `aws-secrets-manager`; `--mysqld.username=$(MYSQL_EXPORTER_USER)` + `MYSQLD_EXPORTER_PASSWORD` env from
  secretKeyRef; `--mysqld.address=$(MYSQLD_ADDRESS)`; perf_schema eventsstatements+tableiowaits (and NO
  other perf_schema.*); INV11 negative (no literal credential); region: Oregon MYSQLD_ADDRESS secretKeyRef
  (no seoul literal), Seoul MYSQLD_ADDRESS literal replica host.
- Opened RED (assertion) 2026-07-15: single baseline render (both overlays exit 0; Oregon 853 / Seoul 851
  lines, clean tail `origin-client-ca`) shows ALL contract tokens 0/0 EXCEPT the pre-existing app token
  `mysql-replica.seoul.danteplanner.internal` = Oregon 0 / Seoul 1 (render line 158 `MYSQL_REPLICA_HOST`,
  PORTLESS — the app configmap-patch value). Logs `/tmp/tdd-red-mysqld-{oregon,seoul}-baseline.log`. The
  RED proof for ALL 4 scenarios. Scenario 4's Seoul gate asserts the PORT-SUFFIXED form
  `mysql-replica.seoul.danteplanner.internal:3306` (0/0 at baseline → gated absolute, no delta ambiguity).
- Closed GREEN at scenario 4 (FINAL). Independent burndown-close run by phase-manager
  (`kubectl kustomize deploy/overlays/{oregon,seoul}` → `/tmp/pm-close-{oregon,seoul}.log`): both exit 0,
  clean tail `origin-client-ca`. ALL contract tokens present region-correctly (full sweep in scenario-4
  green-proof cell). Region-correctness confirmed by python3 yaml parse of the mysqld-exporter Deployment:
  Oregon MYSQLD_ADDRESS = secretKeyRef(mysqld-exporter-dsn/primary-endpoint) with NO literal host; Seoul
  MYSQLD_ADDRESS = literal `mysql-replica.seoul.danteplanner.internal:3306`. mysqld-exporter-dsn count
  Oregon 5 / Seoul 4 — the +1 in Oregon is precisely the primary-endpoint secretKeyRef (Seoul uses a
  literal), a structural confirmation of read-local asymmetry. perf_schema EXACTLY 2, `--no-collect` 0,
  INV11 literal-credential scan empty.

## Pipeline (post-burndown)
- refactor: nothing to consolidate — all 4 scenarios were minimal additive edits to ONE new base manifest
  (deploy/base/mysqld-exporter.yaml) plus two exemplar-following overlay patches; every tdd-green STRUCTURAL
  STRAIN report = none. The two endpoint patches differ ONLY by the mandated region value (Oregon secretKeyRef
  primary vs Seoul literal replica) — that difference IS the read-local requirement (deploy/CLAUDE.md: region
  differences live in overlays, never base), NOT extractable duplication. No refactor spawn (would produce
  zero edits) — same ratified call as phases 01-02.
- verify: PASS (1 round) — phases/03-mysqld-exporter/verification.md. All 8 in-scope render items MET with
  per-region evidence (topology role=data + Oregon-primary-secretKeyRef/Seoul-replica-literal; DSN via
  ExternalSecret mysqld-exporter-dsn; perf_schema exactly eventsstatements+tableiowaits; job label mysqld;
  region-correct render; INV11 repo-grep clean). 3 live/operational items (grants, per-instance FreeableMemory
  gate, "up in both regions") correctly DEFERRED-OUT-OF-SCOPE → phase 11.

## Scenarios
| # | Scenario (one line) | Dossier | Status | Red proof | Green proof |
|---|--------------------|---------|--------|-----------|-------------|
| 1 | base Deployment workload: image v0.19.0, nodeSelector role=data, replicas 1, scrape annotations (job=mysqld unquoted, port 9104), resource req/limits; registered in base kustomization | base-workload.md | closed | baseline 0/0 | PM probe: image+port+job(unquoted) 1/1 both, replicas=1 nodeSelector role=data (parse), 7 later tokens 0/0, render exit0. Files: mysqld-exporter.yaml (new), kustomization.yaml (+1) |
| 2 | DSN via ExternalSecret (INV11): reuse SecretStore aws-secrets-manager → ExternalSecret target `mysqld-exporter-dsn` (username/password/primary-endpoint); Deployment consumes `--mysqld.username=$(MYSQL_EXPORTER_USER)` + `MYSQLD_EXPORTER_PASSWORD` env via secretKeyRef; no literal credential | dsn-external-secret.md | closed | baseline 0/0 | PM probe: mysqld-exporter-dsn 4/4, MYSQL_EXPORTER_USER 2/2, PASSWORD 1/1, `--mysqld.username=$(MYSQL_EXPORTER_USER)` 1/1; INV11 neg: only secretKeyRef(×4)+asm remoteRef paths, no plaintext pwd scalar; S1 intact; S3-4 (MYSQLD_ADDRESS/--mysqld.address/perf_schema/seoul:3306) 0/0. File: mysqld-exporter.yaml only |
| 3 | perf_schema scope (memory gate): exactly `--collect.perf_schema.eventsstatements` + `--collect.perf_schema.tableiowaits`, NO other perf_schema.*, NO --no-collect | perf-schema-scope.md | closed | baseline 0/0 | PM probe both overlays: eventsstatements 1, tableiowaits 1, total collect.perf_schema EXACTLY 2, --no-collect 0; S1-2 intact (image/dsn/username-arg/job); S4 (MYSQLD_ADDRESS/--mysqld.address/seoul:3306) 0/0. File: mysqld-exporter.yaml only |
| 4 | region endpoint patches: base arg `--mysqld.address=$(MYSQLD_ADDRESS)`; oregon patch env MYSQLD_ADDRESS via secretKeyRef(primary-endpoint); seoul patch env literal mysql-replica.seoul.danteplanner.internal:3306; both registered in overlay kustomizations; vacuity Oregon¬seoul-literal, Seoul carries it | region-endpoint-patch.md | closed | baseline 0/0 (:3306 form) | FINAL, PM close probe: all contract tokens 1/1 region-correct; parse: Oregon MYSQLD_ADDRESS=secretKeyRef(mysqld-exporter-dsn/primary-endpoint) NO literal host, Seoul MYSQLD_ADDRESS=literal `...internal:3306`; vacuity o=0/s=2 bare, o=0/s=1 :3306; perf_schema==2, --no-collect 0, INV11 literal-scan empty, replicas1/role=data both. Files: base/mysqld-exporter.yaml, {oregon,seoul}/mysqld-endpoint-patch.yaml (new), {oregon,seoul}/kustomization.yaml (+1) |

## Ratified Convention Decisions (from spawn `## CONVENTION DECISIONS`)
- S2 ExternalSecret remoteRef structure = three flat `data` entries (one `remoteRef.key` each) over a
  single-remoteRef `property` extraction. RATIFIED — Brief explicitly left this open and marked it a
  non-gated token; the flat form mirrors the exemplar `deploy/base/external-secret.yaml` (one secretKey per
  data entry). Not directory-scoped (single-manifest ES authoring taste, derivable from the exemplar — not
  a CLAUDE.md dir rule).

## List Revisions
- (none) — the phase-opening tdd-red confirmed the 4-scenario decomposition with full contract coverage
  and no gap; every scenario closed on the first green attempt; no implementation learning reshaped the list.

## Pipeline (capture / manifest / staged)
- capture: drafts UNAVAILABLE — `meme draft --from-phase` (task path) TIMED OUT at 2min (exit 143), same
  LLM-distillation latency as phase 02; dossier-dir draft not reached. Non-blocking per doctrine; residue
  preserved in this ledger (research facts, D1-D7 settled decisions, ratified S2 remoteRef pick) + the
  dossiers' `## Learnings` for a later manual draft. sweep: RAN (watermark → 957749789b00 = baseline HEAD).
  ~38 candidates, ALL unrelated global lessons (Zod/React/Redis/latency/spec/verification/etc.) — NONE
  touch this phase's mysqld_exporter kustomize diff → all still-true, 0 stale, 0 obsolete. No stale-doc edits.
- directory convention: ADDED one constraint bullet to deploy/CLAUDE.md (RDS-endpoint committability rule
  from ratified D5) — LCA of governed code = deploy/; two-instance convention (app MYSQL_HOST/MYSQL_REPLICA_HOST
  split + this exporter). Staged with the phase. deploy/CLAUDE.md now 6 bullets, well under the ~30-line cap.
- manifest: STAGED — deploy/base/mysqld-exporter.yaml (new), deploy/overlays/{oregon,seoul}/mysqld-endpoint-patch.yaml
  (new), deploy/base/kustomization.yaml (M +1), deploy/overlays/{oregon,seoul}/kustomization.yaml (M +1 each),
  deploy/CLAUDE.md (M, dir-convention bullet), phases/03-mysqld-exporter/{ledger.md,verification.md} (new),
  manifest.json (M, phase-03 entry → done/PASS + artifacts + live-verify & convention followUps). IGNORED
  (pre-existing baseline, surfaced to user, NOT this phase's): deploy/base/spring-daemonset.yaml (M),
  docs/tasks/038-.../requirements.md (M), docs/tasks/038-.../plan/ (?? task scaffolding, same as phases 01-02),
  docs/tasks/014-deploy/* and all other unrelated ?? dirs. No test debris (render-grep phase, no harness); no
  submodule pointer moved by this phase.
- staged: [git diff --cached --stat digest — recorded after stage]

## Pipeline (post-burndown)
- (pending)
