# Phase 06 Scenario Ledger: Handoff amendment

Dossiers: ${XDG_STATE_HOME:-$HOME/.local/state}/claude-build/LimbusPlanner/038-metrics-observability/phase-06/
Baseline (git status --porcelain at phase open, HEAD d86d81d9 — NOT this phase's to stage):
  D  .claude/commands/route-research-for-testing.md
  M  .claude/harness-changelog.md, .claude/hooks/check-output-redirect.sh, .claude/settings.json
  M  .claude/skills/announcement/SKILL.md, .claude/skills/commit-process/SKILL.md
  M  deploy/base/spring-daemonset.yaml
  M  docs/tasks/038-metrics-observability/phases/03-mysqld-exporter/ledger.md
  M  docs/tasks/038-metrics-observability/phases/04-gap-scrape-wiring/ledger.md
  M  docs/tasks/038-metrics-observability/requirements.md
  M  scripts/ops/lib/{dashboard,insights,retention}.sh
  m  static
  ?? many untracked (docs/tasks/*, frontend/*, terraform/*, docs/*.md, plan/, image-analyzer, etc.)
  Full list captured; only handoff.md (M, this phase) + new ledger/verification/manifest are ours.

## Note on phase kind
local-tdd by manifest, but a grep-verifiable doc edit (planner footnote): no red/green pipeline.
Gate = grep assertions on amended handoff.md. Vacuity ("red") proven pre-edit below.

## Acceptance (vacuity / red proof, pre-edit against current handoff.md)
- kube-state-metrics / KSM / object-state — ABSENT
- external_labels / cluster label — ABSENT
- prometheus.io/job / job relabel — ABSENT
- ArgoCD sync / ESO / CoreDNS / etcd snapshot / gap-cluster — ABSENT
- staleness meta / self-scrape — ABSENT
- alerts #7 / #8 / #9 — ABSENT
- Discord — ABSENT
- branch: only line 36 "**dev** branch" present (the stale claim to correct)
- deploy/CLAUDE.md:10 confirms "GitOps: ArgoCD syncs from `main`" — handoff is the sole stale copy

## Amendment items (single artifact, code-writer)
| # | Item | Target | Status | Green proof (grep) |
|---|------|--------|--------|--------------------|
| 1 | dev→main branch correction | handoff.md ~L36-38 | closed | grep: `syncs from the **main** branch`=1, stale `**dev** branch`=0 |
| 2 | KSM object-state subsection (### J) | Selected metrics | closed | grep: `### J.`/`kube-state-metrics`=2, `mechanics.md §1`=1 |
| 3 | Prometheus prerequisites subsection (### K) | Selected metrics | closed | grep: `### K.`/external label/prometheus.io/job=3, `mechanics.md §2`=1 |
| 4 | Coverage-gap clusters subsection (### L) | Selected metrics | closed | grep: control-planes-fail-silent/ArgoCD/ESO/CoreDNS=9, `mechanics.md §4`=1 |
| 5 | §G mysqld_exporter refinement (append to ### G) | existing §G | closed | grep: `mysqld_exporter`=2, `mechanics.md §6`=1 |
| 6 | Alerts #7-9 + staleness meta + Seoul CW-rule + Discord/Slack delivery | Alerts section | closed | grep: #7/#8/#9=1 each, staleness meta=3, CW-datasource=3, Discord=2, `mechanics.md §3`=1 |
| 7 | Impl-order cross-ref "the 6 alerts above" → count-agnostic | Implementation order | closed | grep: `the alerts above`=1, `6 alerts`=0 |

## Follow-up surfaced (out of this phase's scope — for orchestrator/user)
- `mechanics.md §1` documents an 8-entry KSM allowlist with the etcd-snapshot CRS metric
  (`kube_etcd_snapshot_creation_timestamp_seconds`) as a *conditional* extension ("if §4's primary
  route is taken"). Committed phase-02 TOOK that route → live allowlist is NINE entries. mechanics.md
  §1 is stale by one entry. handoff.md `### J` is count-agnostic (references §1, asserts no count),
  so this phase ships internally consistent; but the mechanics.md drift is phase-02's to repair —
  flagged here, not fixed in-phase (surgical precision).

## List Revisions
- After item 6: adding alerts #7-9 made the Implementation-order step-3 cross-reference "the 6
  alerts above" self-contradicting. Added item 7 (surgical count-agnostic fix) — the amendment's
  own edit created the drift, so it is in-scope to repair, not walled off.

## Divergences accepted
- Item 5: writer dropped the "RDS Performance Insights rejected" clause because the existing §G
  Constraint bullet already carries a *standing* "PI likely unsupported — verify before planning"
  statement; a bald "PI rejected" append would contradict the pending-verification nuance the
  settled decision preserves ("PI stays rejected PENDING the instance-class check"). Enumerated
  item-5 deliverables (per-region endpoint, per-instance memory gate, EXPLAIN-ANALYZE-dropped)
  all landed. Accepted — no contradiction of a settled decision.

## Pipeline (post-burndown)
- scoped suite (run by phase manager): 14 passed, 0 failed (grep assertions on amended handoff.md;
  branch main / no dev claim / J,K,L subsections / mysqld §G / alerts #7-9 / staleness meta /
  Seoul CW rule / Discord / mechanics §1-6 refs / no stale "6 alerts")
- refactor: n/a (single doc artifact, nothing to consolidate; tests n/a — grep-verified doc phase)
- verify: PASS after 1 round — phases/06-handoff-amendment/verification.md (11/11 in-scope MET)
- capture: 24 drafts from task + 6 from dossiers (gitignored inactive). sweep: watermark→d86d81d9;
  no active fact made stale/obsolete by this doc amendment (searched GitOps/ArgoCD branch facts —
  none assert the corrected "deploys ride dev" claim; the correction is captured only as a fresh
  inactive draft for user promote). No stale-doc edits, no retire proposals, no directory-convention
  CLAUDE.md edits (this task-doc amendment establishes no new directory convention).
- manifest classification (current status minus Baseline):
  - STAGE: docs/tasks/038-metrics-observability/handoff.md (M — the amendment)
  - STAGE: phases/06-handoff-amendment/ledger.md, verification.md (new — phase artifacts)
  - STAGE: docs/tasks/038-metrics-observability/manifest.json (M — phase-06 entry → done)
  - IGNORED (pre-existing in Baseline, NOT this phase's): requirements.md (M),
    phases/03-mysqld-exporter/ledger.md (M), phases/04-gap-scrape-wiring/ledger.md (M),
    plan/ (?? — build-planner output), plus all repo-wide dirty files outside 038.
- staged (git diff --cached --stat): handoff.md (+62/-), manifest.json (+/-7),
  phases/06-handoff-amendment/ledger.md (+74), verification.md (+25) — 4 files, matches STAGE set.
