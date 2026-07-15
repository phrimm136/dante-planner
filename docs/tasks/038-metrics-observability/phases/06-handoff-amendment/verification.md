# Phase 06: Handoff amendment — PASS

### Suite
No application test runner in scope. This phase is a grep-verifiable doc edit; the
artifact under test is `docs/tasks/038-metrics-observability/handoff.md`. Verification
method is grep with file:line citations (per Brief §Verification method), which replaces
a test-runner suite. All assertions green.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| GitOps branch corrected to `main`; stale `dev` claim absent | Done When #8 (dev→main); Brief assertion 1 | MET | handoff.md:36 `ArgoCD syncs from the **main** branch in both regions (the live GitOps branch)`; grep for `on the **dev** branch` / `dev branch` returns nothing | grep assertion (positive `main` at :36, negative `dev` absent) |
| `### J.` kube-state-metrics / object-state subsection present | Description item 6; Brief assertion 2 | MET | handoff.md:144 `### J. Kubernetes object state (kube-state-metrics)`; refs `mechanics.md §1` at :152 | grep `^### J\.` → :144 |
| `### K.` Prometheus prerequisites (external label + `prometheus.io/job` relabel + self-scrape) | Description item 6; Brief assertion 2 | MET | handoff.md:154 `### K.`; :155 `cluster` external label; :157 `prometheus.io/job` relabel; :159 self-scrape; refs `mechanics.md §2` at :160 | grep all three primitives present at :155/:157/:159 |
| `### L.` coverage-gap clusters (ArgoCD/ESO/CoreDNS/etcd; "control planes fail silent, data planes fail loud") | Description item 6; Brief assertion 2 | MET | handoff.md:162 `### L.`; :163 rationale verbatim; :166 ArgoCD+ESO; :167 etcd fsync; :174 CoreDNS; refs `mechanics.md §4` at :175 | grep each cluster + rationale phrase present |
| §G refinement: `mysqld_exporter` per region folded into `### G. DB depth` | Description item 6; Brief assertion 3 | MET | handoff.md:112–117 `**Refinement:** mysqld_exporter runs one instance **per region**...` under `### G. DB depth` (:100); refs `mechanics.md §6` at :117 | grep `mysqld_exporter runs one instance` → :112 |
| Alerts #7–9 present | Done When #8; Brief assertion 4 | MET | handoff.md:186 `7. Node not Ready`; :187 `8. Backend DaemonSet ready count == 0`; :188 `9. Container stuck waiting CrashLoop/ImagePull` | grep `^7\.`/`^8\.`/`^9\.` → :186–188 |
| Staleness meta-alert bullet present | Brief assertion 4 | MET | handoff.md:189 `**Staleness meta-alert** (unnumbered, "M" in mechanics)` | grep → :189 |
| Alert #1 named as Grafana CloudWatch-datasource rule (Seoul replica) | Brief assertion 4 | MET | handoff.md:178–179 `Seoul replica DatabaseConnections == 0 ... a **Grafana CloudWatch-datasource** rule` | grep → :178 |
| Discord + Slack delivery line present | Brief assertion 4 | MET | handoff.md:193 `Discord webhook (primary) + Slack incoming webhook (fallback)` | grep → :193 |
| New subsections reference `mechanics.md §N` (not transcribing tables) | Brief assertion 5 (design discipline) | MET | handoff.md:152 `§1`, :160 `§2`, :175 `§4`, :194 `§3`, :117 `§6` — each new/refined subsection points to mechanics for exact lists | grep `mechanics.md §` → 5 refs |
| No stale "6 alerts above" cross-reference remains | Brief assertion 6 | MET | grep `6 alerts above` returns nothing; :201 uses generic `the alerts above` (count-agnostic, correct with 9 alerts) | grep negative `6 alerts above` absent |

### Gaps
- None. All 11 in-scope assertions MET with file:line evidence.
