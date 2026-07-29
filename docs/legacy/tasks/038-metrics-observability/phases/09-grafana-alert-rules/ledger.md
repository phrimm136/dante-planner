# Phase 09 Scenario Ledger: Grafana-managed alert rules

Dossiers: ~/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-09/
Baseline: HEAD e08a3ba6. manifest.json is CLEAN at open (committed → my phase-09 edit is cleanly
attributable). The working tree carries a heavy PRE-EXISTING dirty set unrelated to this phase — NOT
this phase's to stage: `.claude/*` (harness/skills/settings/commands/route-research delete),
deploy/base/spring-daemonset.yaml (M), scripts/ops/lib/{dashboard,insights,retention}.sh (M), static
submodule pointer (m), requirements.md (M), phases/03 + phases/04 ledgers (M), and every untracked
docs/tasks/* + terraform/* + frontend/* path, plus plan/ (untracked task-level artifact). Phase-09 delta
at open = none (phases/09-grafana-alert-rules did not exist).

## Nature of phase — operational-only, no repo artifacts (mirrors phases 07/08)
Kind: infra (Grafana Cloud provisioning; consent-gated). `plan/phase-09.md`: "Files: none in-repo
(operational — Grafana-managed alert rules; §5)"; `mechanics.md` §5 provisioning ledger is titled
"operational budget — nothing here touches the repo". There is NO production/test code to author, NO
local test suite, and NO static repo artifact to render-check. The entire deliverable lives in Grafana
Cloud (alert rules #7–9, staleness meta M, and the gap-cluster alerts) and is created by the USER under
the infra consent boundary. Accordingly:
- tdd-red / tdd-green / tdd-refactor burndown is INAPPLICABLE — no assertions exist to drive red, no code
  to write, no minimum-code debt to consolidate. Doctrine: "do not manufacture a vacuous green."
- The pipeline collapses to: author this ledger (operational runbook + PromQL static sanity-check +
  deferred-drill verification record) → spec-verifier static certification (runbook-completeness against
  §3 rows 7–9/M + §4 gap alerts; PromQL structural validity against the shipped KSM allowlist / scrape
  jobs; INV11 negative-leak; every live/drill item DEFERRED-LIVE or BLOCKED-per-spec) → capture →
  docs-only staged commit (ledger + verification.md + manifest.json) → phase closes `authored`, not
  `done` (rule creation + routing + drills are all the user's, consent-gated / step-blocked).

## Binding spec rulings inherited (requirements.md; NOT re-litigated here)
- Rule-evaluation drills — INV2, INV5, INV10, and the rule-firing halves of INV4/INV9 — are DEFERRED to
  step-3 remote_write wiring. Grafana-managed rules evaluate on remote_written data; remote_write is out
  of this task's Target. This phase AUTHORS the rules; drill-fire is out of scope (BLOCKED-A, plan §).
- Cert-expiry (`probe_ssl_earliest_cert_expiry`, rider on §F blackbox) and clock-skew
  (`node_timex_offset_seconds`, rider on §E node_exporter) alerts are DEFERRED to handoff step-2 with
  their exporters — no metric source exists here (blackbox + node_exporter NOT deployed, handoff step-2).
  They cannot be authored against a real seam in this phase (BLOCKED-B, plan §). Excluded from scope.

## Scope filter — the SEVEN authored rules (and what is deliberately excluded)
This phase's external contract (plan/phase-09.md) scopes exactly seven Grafana-managed alert rules:
`#7` Node-not-Ready, `#8` Backend-DaemonSet-unready, `#9` Container-waiting, `M` per-cluster
staleness-meta, and three gap-cluster alerts — `G-argo` (ArgoCD OutOfSync/Degraded 30m), `G-eso`
(ESO ExternalSecret not-Ready sustained), `G-etcd` (etcd-snapshot dead-man past 1.5× interval).

`mechanics.md` §4 lists MORE rows than these seven; the extras are NOT alerts authored here:
- **CP/etcd health scrape**, **Deploy markers**, **CoreDNS** — these are *scrape/wiring* contracts
  owned by other phases (04 gap-scrape, 05 deploy-markers), not alert rules. Excluded from this phase.
- **Cert expiry** (`probe_ssl_earliest_cert_expiry`) and **Clock skew** (`node_timex_offset_seconds`)
  — BLOCKED-B: their exporters (blackbox §F, node_exporter §E) are handoff step-2, NOT deployed
  (`handoff.md:32-33`, absent from `deploy/`). No metric seam exists to author against here. Deferred
  to step-2 with their exporters. Excluded from this phase.

## Query-datasource + No-Data reality (why the phase ships `authored`, not `done`)
All seven rules query the **Grafana Cloud hosted Prometheus datasource** (the remote_write *target*),
NOT CloudWatch (that was phase 08 rule S). remote_write is out of this task's Target and UNWIRED
(BLOCKED-A) — so the `up{cluster=...}` / KSM / exporter series these rules match do NOT yet exist in
Grafana Cloud. Consequence, and the crisp boundary the phase honors:
- `#7`,`#8`,`#9`,`G-argo`,`G-eso`,`G-etcd` are *presence/threshold* rules (`==`, `=~`, staleness of a
  present timestamp): on absent input they evaluate to **No Data**, not firing. The user creates them
  now; they sit benign in No-Data until step-3 delivers data; their *firing* drills (INV4/#7, INV8-half
  observability is scrape-side, INV9/G-etcd) defer to step-3 (BLOCKED-A). Set each rule's **No-Data
  handling to a non-paging state** (Keep Last State / OK, NOT Alerting) so the pre-wiring emptiness
  never pages.
- `M` is deliberately an **absent-detector** (`absent_over_time(up{cluster=...}[~10m])` returns `1` on
  absence). It therefore FIRES on the very absence that the pre-remote_write state guarantees — so `M`'s
  *creation* must be sequenced WITH step-3 remote_write (create it when `up{cluster=...}` is actually
  flowing, so that absence becomes a real signal rather than the trivial pre-wiring truth). Its full
  spec is pinned below and is `authored` now; the user activates it at step-3. This is the INV10 vantage.

## Common delivery contract (routing — stated ONCE, applies to all seven)
Every rule routes to the phase-07 **Default notification policy → `alert-dual-channel`** contact point,
so each firing reaches **Discord (primary) AND Slack (fallback)** via that one contact point's two
integrations — redundancy, not failover. Do NOT add a lone match-all nested child route: the phase-07
ledger records that a match-all child delivers one-channel-only and FAILS INV6. Ensure each rule's
labels resolve to the Default policy; if a rule is created under a folder/group carrying its own policy,
confirm it still resolves to `alert-dual-channel`.

## Rule mechanics (one table per rule — expression / for / threshold / datasource / source)
All datasource = **Grafana Cloud hosted Prometheus** (remote_write target) unless noted.

**#7 — Node not Ready** (`mechanics.md` §3 row 7; requirements Decisions #7–9)
| Element | Value |
|---|---|
| Expression | `kube_node_status_condition{condition="Ready",status="true"} == 0` (per node) |
| Threshold | `== 0` (Ready condition not true) |
| `for` | **15m** — outlasts a routine surge window (taint→drain→scale-in→node-delete) so deploys do not page (INV5); catches orphaned NotReady nodes (no cloud-controller-manager) + kubelet death |
| Metric source | KSM allowlist entry `kube_node_status_condition` (phase 02, verified in `deploy/base/kube-state-metrics.yaml:87`) |

**#8 — Backend DaemonSet unready** (`mechanics.md` §3 row 8)
| Element | Value |
|---|---|
| Expression | `kube_daemonset_status_number_ready{daemonset="backend"} == 0` |
| Threshold | `== 0` (zero ready pods) |
| `for` | **5m** — control-plane truth complementing the scrape-truth `absent(up)` dead-man |
| Metric source | KSM `kube_daemonset_status_number_ready`; daemonset name `backend` SETTLED (`deploy/base/spring-daemonset.yaml:6` — the mechanics "verify at impl" is closed) |

**#9 — Container stuck waiting** (`mechanics.md` §3 row 9)
| Element | Value |
|---|---|
| Expression | `kube_pod_container_status_waiting_reason{reason=~"CrashLoopBackOff\|ImagePullBackOff"} == 1` |
| Threshold | `== 1` (container in a listed waiting reason) |
| `for` | **10m** — tolerates transient pull retries; catches Seoul ECR replication lag stalling a rollout |
| Metric source | KSM `kube_pod_container_status_waiting_reason` (phase 02 allowlist) |

**M — Per-cluster staleness meta (absent-detector; INV10)** (`mechanics.md` §3 row M)
| Element | Value |
|---|---|
| Expression | one instance per cluster value: `absent_over_time(up{cluster="oregon"}[10m])` and `absent_over_time(up{cluster="seoul"}[10m])` (returns `1` when NO `up` series for that cluster arrived in 10m) |
| Threshold | `== 1` (no recent data from that cluster's Prometheus) |
| `for` | ~immediate / short (`absent_over_time` already encodes the 10m window; a small `for` avoids single-scrape flaps) |
| Metric source | Prometheus self-scrape `up` series carrying `external_labels.cluster` = `oregon`\|`seoul` (phase 01; `deploy/base/prometheus.yaml:40-41` `cluster: ${CLUSTER_NAME}`, overlays set oregon/seoul) |
| **Sequencing** | CREATE WITH step-3 remote_write (absent-detector fires on pre-wiring absence — see No-Data reality above). Evaluated in Grafana Cloud, external to both regions, so it fires "from the surviving vantage" when one region's egress dies. |

**G-argo — ArgoCD OutOfSync/Degraded** (`mechanics.md` §4 ArgoCD row; requirements Decisions cluster (1))
| Element | Value |
|---|---|
| Expression | sustained OutOfSync OR Degraded on `argocd_app_info`, e.g. `argocd_app_info{sync_status="OutOfSync"} == 1` OR `argocd_app_info{health_status="Degraded"} == 1` |
| Threshold | app OutOfSync or Degraded |
| `for` | **30m** (requirements Decisions / plan) |
| Metric source | ArgoCD application-controller scrape, job `argocd` (phase 04) |
| **Confirm-at-creation** | verify the exact metric name + the `sync_status`/`health_status` label spelling against the live application-controller `/metrics` (`:8082`) before pinning the expression — exporter label spellings are the exporter's own, not settled in-repo |

**G-eso — ESO ExternalSecret not-Ready** (`mechanics.md` §4 ESO row; requirements Decisions cluster (1))
| Element | Value |
|---|---|
| Expression | any ExternalSecret whose Ready condition is False, sustained, e.g. `externalsecret_status_condition{condition="Ready",status="False"} == 1` |
| Threshold | ExternalSecret not Ready |
| `for` | sustained (fail-open secrets surface — page on persistence, not a single reconcile blip; pin the exact window at creation, default ~15m) |
| Metric source | external-secrets controller scrape, job `external-secrets` (phase 04) |
| **Confirm-at-creation** | verify the exact ESO metric + condition/status label spelling against the live ESO controller `/metrics` (`:8080`) before pinning |

**G-etcd — etcd snapshot dead-man** (`mechanics.md` §4 etcd row; requirements Decisions cluster (2))
| Element | Value |
|---|---|
| Expression | `(time() - kube_etcd_snapshot_creation_timestamp_seconds) > (1.5 * <snapshot_interval_seconds>)` per cluster |
| Threshold | last snapshot older than 1.5× the k3s snapshot interval |
| `for` | short (the 1.5× multiplier is itself the tolerance; a small `for` avoids a scrape flap) |
| Metric source | **shipped realization of §4's PRIMARY route** — `kube_etcd_snapshot_creation_timestamp_seconds` landed in the phase-02 KSM allowlist (`deploy/base/kube-state-metrics.yaml:87`), so the ETCDSnapshotFile-CR-via-KSM primary route is already live; NO S3-object-age fallback needed |
| **User-pinned placeholder** | `<snapshot_interval_seconds>` = the live k3s etcd-snapshot cron interval (a live value, never committed — mirrors phase 08's instance-id placeholder) |

## PromQL static sanity-check (promtool ABSENT — manual structural validation)
`promtool` is not installed (`command -v promtool` empty), no `prometheus` binary, and no local
`prom/prometheus` docker image — same absence phase 04 recorded. No full-parse validator is available;
per doctrine I say so rather than skip. In its place, a MANUAL structural check (syntax-level, not a
semantic parse) over the seven expressions:
- **Brace/paren/quote/bracket balance**: all seven expressions balance `{}`,`()`,`""`,`[]` — checked
  character by character. PASS.
- **Operator/regex validity**: `==`,`>`,`*`,`-` are well-formed binary/arithmetic ops; `=~` in #9 pairs
  a valid Go/RE2 alternation `"CrashLoopBackOff|ImagePullBackOff"` (escaped `\|` in the doc table is the
  Markdown escape, the rule expression uses a literal `|`); `absent_over_time(...[10m])` and `time()`
  are valid PromQL functions with correct arity. PASS.
- **Metric names resolve against SHIPPED series** (the check promtool could not do better without a live
  TSDB): `kube_node_status_condition`, `kube_daemonset_status_number_ready`,
  `kube_pod_container_status_waiting_reason`, `kube_etcd_snapshot_creation_timestamp_seconds` are ALL
  present in the phase-02 KSM allowlist (`kube-state-metrics.yaml:87`, verified); `up` is the Prometheus
  self-scrape series carrying `cluster` (phase 01). `argocd_app_info` / `externalsecret_status_condition`
  are exporter-owned and flagged confirm-at-creation (not resolvable in-repo by design).
- **Label-matcher shape**: every matcher uses a defined KSM/allowlist label (`condition`,`status`,
  `daemonset`,`reason`,`cluster`) with valid `=`/`=~` operators. PASS.
Verdict: STRUCTURALLY VALID at the syntax level for all seven; semantic/type correctness against a live
TSDB is DEFERRED-LIVE (needs remote_written data — BLOCKED-A), same class of gap as the firing drills.

## Operational runbook (USER-executed, consent-gated) — INV11: snapshot-interval + exporter endpoints live ONLY in Grafana Cloud / the cluster, never the repo
Owner of every live value (snapshot interval, exporter label confirmations, folder/policy routing): user
(mechanics.md §5). Exact steps:

1. **Create rules #7, #8, #9 on the hosted-Prometheus datasource.** Grafana Cloud → Alerting → Alert
   rules → New alert rule; datasource = the Grafana Cloud hosted Prometheus (remote_write target). Enter
   each expression from its table verbatim, set the `for` (15m / 5m / 10m) and threshold as tabled. Set
   **No-Data handling to a non-paging state** (Keep Last State or OK, NOT Alerting) — pre-step-3 the KSM
   series are absent and must not page. Name them e.g. `node-not-ready`, `backend-daemonset-unready`,
   `container-waiting-backoff`.
2. **Create the three gap-cluster rules** (`G-argo`, `G-eso`, `G-etcd`) on the same datasource. For
   `G-argo`/`G-eso`, FIRST confirm the exact metric + label spelling against the live exporter `/metrics`
   (argocd application-controller `:8082`, ESO controller `:8080`) — pin the expression to what the
   exporter actually emits, then set `for` (30m argo; sustained eso). For `G-etcd`, substitute the live
   k3s snapshot interval for `<snapshot_interval_seconds>` (keep it OUT of the repo — INV11). Same
   non-paging No-Data handling.
3. **Author (spec-pin) rule M, but SEQUENCE its creation with step-3 remote_write.** M is an
   absent-detector — creating it before `up{cluster=...}` flows makes it fire on the trivial pre-wiring
   absence. When step-3 lands, create one M instance per cluster: `absent_over_time(up{cluster="oregon"}
   [10m]) == 1` and the `seoul` twin, short `for`. This is the INV10 staleness vantage.
4. **Route every rule to the dual-channel policy.** Ensure each rule's labels resolve to the phase-07
   **Default notification policy → `alert-dual-channel`** (Discord + Slack). No lone match-all nested
   child (INV6 trap). Confirm resolution if any rule sits under a folder/group with its own policy.

## Verification record — rule-evaluation drills (BLOCKED-A, step-3 remote_write)
DEFERRED to step-3. Grafana-managed rules evaluate on remote_written data; remote_write is out of this
task's Target. Per the binding spec ruling (requirements.md), these drills cannot run in this phase:
- **INV4 (rule half)** — stop a disposable app node's k3s agent; `#7` fires after 15m. DEFERRED-STEP3.
- **INV5** — observe a full surge (1→2→1) with rules active; ZERO firings from `#7`. DEFERRED-STEP3.
- **INV9 (G-etcd fire)** — suspend the snapshot schedule; `G-etcd` fires past 1.5× interval.
  DEFERRED-STEP3.
- **INV10 (M fire)** — block one region's Prometheus egress; `M` fires from the surviving vantage.
  DEFERRED-STEP3 (and gated on M's own step-3 creation, above).
- **INV8** — GitOps drift visible in `argocd_app_info` within one scrape: the *observability* half is
  scrape-side (phase 04); the `G-argo` *firing* leg (30m sustained → page) also needs remote_written
  argocd series → DEFERRED-STEP3.
Contact-point delivery (INV6) was drill-defined in phase 07/08 (dual-channel test-fire) and is NOT
re-opened here; the rule-evaluation drills above are the only deferred legs this phase adds.

## INV11 — negative-leak (my independent static check)
Phase 09 authors NO repo artifact (operational-only), so it cannot introduce a leak; the runbook keeps
the k3s snapshot interval, exporter endpoint confirmations, and all Grafana routing OUT of the repo
(they are live/user-owned values). Confirmed by a repo-wide grep for secret shapes (see Pipeline).

## Acceptance
- No local acceptance test exists (operational-only phase). Behavioral acceptance = the deferred
  rule-evaluation drills (BLOCKED-A, step-3). Static acceptance = spec-verifier certifies
  runbook-completeness against §3 rows 7–9/M + §4 gap alerts, PromQL structural validity, and INV11.

## Scenarios
| # | Scenario | Dossier | Status | Red proof | Green proof |
|---|----------|---------|--------|-----------|-------------|
| — | none — operational-only phase, no testable code (see "Nature of phase") | — | n/a | n/a | n/a |

## List Revisions
- none — no burndown.

## Pipeline (post-burndown)
- burndown: not applicable (operational-only; no code, no local tests).
- refactor: not applicable (no authored code to consolidate).
- PromQL static sanity-check: promtool ABSENT (no binary, no prometheus, no local prom docker image —
  same as phase 04); manual structural check recorded in the runbook — all seven expressions balance
  braces/parens/quotes/brackets, operators/regex/functions well-formed, and every KSM/self-scrape metric
  name resolves against SHIPPED series (`kube-state-metrics.yaml:87` allowlist + phase-01 `up`/`cluster`).
  STRUCTURALLY VALID; semantic/type check against a live TSDB DEFERRED-STEP3 (needs remote_written data).
- refactor: not applicable (no authored code).
- scoped "suite" (my independent static check — no unit suite exists): INV11 secret-shape grep over
  `phases/09-grafana-alert-rules/` (webhook URLs, `glsa_*` Grafana tokens, `AKIA*`, snapshot-interval
  numerals) → EXIT 1, ZERO matches. Phase authored only this docs ledger (no repo code artifact) → cannot
  have introduced a leak. GREEN (clean).
- verify: PASS after 1 round — spec-verifier statically certified: all SEVEN rules
  (#7/#8/#9/M/G-argo/G-eso/G-etcd) present + internally consistent (expression/threshold/for/datasource/
  routing); shared dual-channel routing stated once + INV6 match-all trap honored; datasource + No-Data
  boundary explicit (hosted-Prometheus, remote_write-unwired → six threshold rules sit No-Data non-paging,
  M sequenced with step-3); PromQL manual check honest with metric names resolving against shipped series;
  scope exclusions present (CP/etcd-health/deploy-markers/CoreDNS = other phases; cert/skew = BLOCKED-B
  step-2); INV11 leak grep clean. Verifier INDEPENDENTLY re-confirmed 4 load-bearing facts (allowlist :87
  all 4 metrics, daemonset name `backend`, `cluster: ${CLUSTER_NAME}`, leak grep EXIT 1). All open items
  DEFERRED-STEP3 (rule-firing drills INV4/5/9/10 + G-argo half of INV8) / DEFERRED-LIVE (rule creation +
  routing), correct by design. NO UNTESTABLE. Report: phases/09-grafana-alert-rules/verification.md. One
  divergence (dossier `## Divergences`): datasource stated once as a blanket + route in the shared-contract
  section rather than per-rule rows — certified substance MET per the Brief's own "stated once" framing;
  cosmetic, no send-back (not a decision/spec conflict, does not reshape scope).
- capture: `meme draft --from-phase` (task dir) drafted 15 gitignored candidates — 6 Grafana/observability
  decision-facts (alert-eval-outside-monitored-region; independent-fallback-channels; app-embedded-query-
  monitoring coupling; read-local query-telemetry; census-vs-drill observability split; K8s apiserver
  authoritative-for-object-state gotcha) + a "control-planes-fail-silent" lesson + others. Dossier-dir draft:
  nothing to distill (no non-derivable `## Learnings` residue). sweep: watermark advanced to e08a3ba6f4b8;
  ~40 candidates reviewed — NONE made stale/obsolete by this docs-only runbook diff (all unrelated Spring/
  React/Redis/Zod/latency/git/testing/spec lessons; the two "un-retire?"-marked facts — FE/BE split-commit
  discipline, meme portable-tool paths — are untouched by this diff). No docs edited, no retire proposals.
  No directory-scoped convention decisions (the Grafana realization decisions — No-Data non-paging,
  M-absent-detector sequencing, dual-channel routing — are meme decision-facts, not repo-directory code
  rules) → no CLAUDE.md edits.
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: phases/09-grafana-alert-rules/ledger.md (new), phases/09-grafana-alert-rules/verification.md
  (new), docs/tasks/038-metrics-observability/manifest.json (M — phase-09 entry ONLY; diff verified to
  touch no other entry). NO code (operational-only — nothing touches the repo).
  IGNORED: the entire pre-existing baseline-dirty set (.claude/* incl. route-research delete,
  deploy/base/spring-daemonset.yaml, scripts/ops/lib/*, static submodule pointer, requirements.md M,
  phases/03+04 ledgers M, all untracked docs/tasks/* + terraform/* + frontend/*) — NOT this phase's;
  plan/ (untracked task-level artifact); LimbusPlanner/.meme/drafts + drafts/ (gitignored capture output).
  Dossiers live outside the repo — never staged. NO stray phase-09-attributable file exists.
- manifest status: phase 09 → authored (verdict PASS static; live rule creation + routing + rule-evaluation
  drills consent-gated / step-3-blocked).
- staged: git diff --cached --stat = 3 files — phases/09-grafana-alert-rules/ledger.md (new, +266),
  phases/09-grafana-alert-rules/verification.md (new, +73), docs/tasks/038-metrics-observability/
  manifest.json (M, +11/-2, phase-09 entry only). Matches the STAGED set exactly. Docs-only; no code.
  manifest.json re-validated as parseable JSON. Commit is the USER's — proposal returned, not run here.
