# Phase 08 Scenario Ledger: Seoul replica silent-zero rule

Dossiers: ~/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-08/
Baseline: HEAD 3ec1aa2b. manifest.json is CLEAN at open (committed → my phase-08 edit is cleanly
attributable). The working tree carries a heavy PRE-EXISTING dirty set unrelated to this phase — NOT
this phase's to stage: `.claude/*` (harness/skills/settings/commands), backend SSE + planner
sources/tests (PlannerCommandService/PlannerSyncEventService/SseEnvelope/SsePublisher/SseRedisSubscriber
+ tests + new PlannerSyncEventServiceTest), deploy/base/spring-daemonset.yaml, scripts/ops/lib/*, static
submodule pointer, requirements.md (M), phases/03 + phases/04 ledgers (M), and every untracked
docs/tasks/* + terraform/* + frontend/* path, plus plan/ (untracked task-level artifact). Phase-08 delta
at open = none (phases/08-seoul-replica-alert did not exist).

## Nature of phase — operational-only, no repo artifacts (mirrors phase 07)
Kind: infra (Grafana Cloud provisioning; consent-gated; deadline/drill-verified). `plan/phase-08.md`:
"Files: none in-repo (operational — Grafana CW-datasource rule; §5)"; `mechanics.md` §5 provisioning
ledger is titled "operational budget — nothing here touches the repo". There is NO production/test code
to author, NO local test suite, and NO static repo artifact to render-check. The entire deliverable lives
in Grafana Cloud (one alert rule on the CloudWatch datasource) and is created by the USER under the infra
consent boundary. Accordingly:
- tdd-red / tdd-green / tdd-refactor burndown is INAPPLICABLE — no assertions exist to drive red, no code
  to write, no minimum-code debt to consolidate.
- The pipeline collapses to: author this ledger (operational runbook + drill verification record) →
  spec-verifier static certification (INV11 negative-leak for AWS creds/account-id + runbook-completeness
  against §3 row S / §5 CW-datasource credentials; every live item DEFERRED-LIVE) → capture → docs-only
  staged commit (ledger + verification.md + manifest.json) → phase closes `authored`, not `done` (the CW
  datasource creation, the rule creation, the routing, and the test-fire drill are all the user's,
  consent-gated).

## DEADLINE — 2026-07-27 (postmortem action item, handoff.md:138)
The ~30s cross-Pacific read outage went undetected because the Seoul RDS replica sat at 0 connections
with nothing alerting. This rule is the postmortem's remediation, due **2026-07-27**. The phase ships
`authored`; the user acting on the live steps below BEFORE that deadline is the entire point of the phase.
This rule is independent of the remote_write-blocked phase-09 rules — the CW datasource needs only the
Grafana stack plus read-only AWS credentials (handoff.md:138-139), so it can and must ship ahead of them.

## External contract (plan/phase-08.md)
A Grafana alert rule on the CloudWatch datasource that fires when the Seoul RDS replica reports
`DatabaseConnections == 0` sustained 15m, routed to the phase-07 notification policy (Discord primary /
Slack fallback via the single `alert-dual-channel` contact point on the Default policy).

## Rule S mechanics (mechanics.md §3 row S + delivery contract)
| Element | Value | Source / rationale |
|---|---|---|
| Datasource | The Grafana **CloudWatch** datasource (NOT Prometheus) | §3: "CW datasource"; born independent of remote_write |
| Namespace | `AWS/RDS` | CloudWatch RDS metrics namespace |
| Metric | `DatabaseConnections` | §3 row S / requirements Decisions |
| Dimension | `DBInstanceIdentifier` = **the Seoul RDS replica instance id** (user-owned placeholder — the exact id is a live value, never committed, mirroring how phase 07 treated the webhook URLs) | Must scope to the Seoul replica, not the Oregon primary |
| Statistic + period | **Maximum over a 1-minute period** (realization decision, below) | `== 0` is ambiguous without these — a Maximum over the period means a single connection anywhere in the minute clears that data point |
| Threshold | `== 0` | §3 row S |
| `for` (pending→firing) | **15m** | §3 row S "for 15m"; outlasts a routine brief idle window, catches genuine sustained silence |
| Routing | Default notification policy → `alert-dual-channel` (phase 07) | Delivery contract: Discord primary + Slack fallback, redundancy every firing |

### Realization decision — statistic Maximum, 1-minute period (phase-manager ruling; user may tune)
`DatabaseConnections == 0 for 15m` is not fully specified until the reduce statistic and evaluation period
are pinned (§3 gives only the expression sketch and the `for`). Chosen: **Maximum, 1-minute period**, so a
firing means every one of 15 consecutive 1-minute maxima was exactly zero — i.e. genuinely NOT ONE
connection reached the replica for a sustained 15m. Maximum (not Average/Minimum) is the conservative pick:
any single connection in a minute makes that minute's max non-zero and resets `pending`, so the rule pages
only on true, uninterrupted silence — matching the postmortem signal (a replica serving zero reads), not a
transient dip. The user may widen the period or relax the statistic; this is the trap-free default.

### Scope note — literal-zero, NOT absent-metric (non-blocking; prevents later misreading)
This rule targets the replica being **up and emitting a literal `0`** (the postmortem scenario: replica
alive, zero reads reaching it). Instance-down / metric-absent (No Data) is a **separate** concern and a
**separate** alert — do NOT fold it into this rule by treating No-Data as a firing condition. Configure the
rule's No-Data handling as its own state (Grafana default: separate No-Data alert or OK), not as `== 0`.

## Operational runbook (USER-executed, consent-gated) — INV11: AWS credentials + account id + instance id live ONLY in Grafana Cloud / AWS, never in the repo
Owner of the CW-datasource AWS read credentials and the replica instance id: user (mechanics.md §5). Exact steps:

1. **Provision read-only AWS credentials for CloudWatch (user-owned, §5).** Create (or reuse) an IAM
   principal with **only** the read-only permissions Grafana's CloudWatch datasource documents
   (`cloudwatch:ListMetrics`, `cloudwatch:GetMetricData`/`GetMetricStatistics`, and the tag/dimension
   list actions in Grafana's published CloudWatch-datasource IAM policy — follow the official Grafana
   CloudWatch datasource docs for the exact current permission set; do not hand-transcribe an IAM policy
   from memory). Keep the access key / role ARN and the AWS account id OUT of the repo (INV11) — they live
   only in the Grafana Cloud datasource config.
2. **Create the Grafana CloudWatch datasource.** Grafana Cloud → Connections → Data sources → Add data
   source → **CloudWatch**; set the default region to the Seoul RDS replica's region; supply the read-only
   credentials from step 1 (access key or, preferred, an IAM role / assume-role ARN). Save & test.
3. **Create alert rule S on the CloudWatch datasource.** Grafana Cloud → Alerting → Alert rules → New alert
   rule; datasource = the CloudWatch datasource from step 2. Query: namespace `AWS/RDS`, metric
   `DatabaseConnections`, dimension `DBInstanceIdentifier` = the Seoul replica instance id, statistic
   **Maximum**, period **1 minute**. Condition: **IS EQUAL TO 0**. Pending period (`for`): **15m**. Set the
   rule's **No Data** handling to a separate state (NOT Alerting) so this rule stays literal-zero-scoped
   (scope note above). Name it e.g. `seoul-replica-silent-zero`.
4. **Route it to the dual-channel policy.** Ensure the rule's labels match the **Default notification
   policy** (created in phase 07 → `alert-dual-channel`), so a firing reaches Discord AND Slack via the one
   contact point's two integrations. No nested route is needed (phase-07 ledger: a lone match-all child
   would deliver one-channel-only and FAIL INV6). If the rule is created under a folder/group with its own
   policy, confirm it still resolves to `alert-dual-channel`.

## Verification record — drill (mechanics.md §7 step 3 / INV6 through-policy leg)
DEFERRED-LIVE — user-executed against live Grafana Cloud, consent-gated. This is the FORWARD close of the
INV6 through-policy drill that phase 07's verification.md deferred to "the Seoul replica rule once it
exists" (phase-07 verification.md Gaps §; live context confirms the through-policy leg is not yet
confirmed). Phase 08 IS that rule — so ONE drill here closes both phase 08's own behavioral proof and
phase 07's deferred INV6 leg; the user runs one drill, not a separate scratch rule. (Forward note only —
phase 07's committed ledger/verification.md are NOT edited; task-level verification reconciles INV6.)

Drill steps:
1. Force rule S to fire once — either drive the Seoul replica's `DatabaseConnections` to a genuine 0 in a
   controlled window, or temporarily edit the rule's condition to a trivially-true form (e.g. `>= 0`) so it
   fires through the SAME notification policy, then revert. (Editing the condition preserves the routing
   path, which is the leg being proved.)
2. Confirm the SAME firing produces a message in **Discord AND Slack** via `alert-dual-channel` — this
   proves the routing (phase-07 step 4) actually delivers, which a contact-point Test alone cannot (a Test
   bypasses policy routing — necessary, not sufficient).
3. Revert any temporary condition change so rule S is back to `DatabaseConnections == 0 for 15m`; record
   the outcome (both channels received the same firing via the Default policy) in this phase's verification
   record. This also lets the user tick phase 07's Done-When "delivery path drill-fired once via
   contact-point test (INV6)".

## INV11 — negative-leak (my independent static check)
Phase 08 authors NO repo artifact (operational-only), so it cannot introduce a leak; the runbook keeps the
AWS credentials, account id, and replica instance id out of the repo (steps 1–2). Confirmed by a repo-wide
grep for AWS-secret shapes (see Pipeline).

## Acceptance
- No local acceptance test exists (operational-only phase). Behavioral acceptance = the live drill above
  (DEFERRED-LIVE, user-executed). Static acceptance = spec-verifier certifies runbook-completeness against
  §3 row S / §5 + INV11 negative-leak.

## Scenarios
| # | Scenario | Dossier | Status | Red proof | Green proof |
|---|----------|---------|--------|-----------|-------------|
| — | none — operational-only phase, no testable code (see "Nature of phase") | — | n/a | n/a | n/a |

## List Revisions
- none — no burndown.

## Pipeline (post-burndown)
- burndown: not applicable (operational-only; no code, no local tests).
- refactor: not applicable (no authored code to consolidate).
- scoped "suite" (my independent static check — no unit suite exists): INV11 AWS-secret leak grep over the
  repo (excluding docs/tasks, node_modules, .terraform) for `AKIA[0-9A-Z]{16}` / `aws_secret_access_key` /
  `arn:aws:iam::<12-digit>` across yaml/yml/java/ts/tsx/tftpl/tf/sh/json → EXIT 0 with 11 hits, ALL
  pre-existing `${{ secrets.AWS_SECRET_ACCESS_KEY }}` GitHub-Actions interpolations in .github/workflows/*.yml
  (secrets-manager references, NOT plaintext), NONE phase-08-attributable (phase 08 authored no repo
  artifact; `git status --porcelain .github/workflows/` empty; setup-cloudwatch.yml predates at 044ca37f).
  No committed AWS secret / account id / instance id. GREEN (clean by provenance).
- verify: PASS after 1 round — spec-verifier certified the static scope: Item A INV11 AWS-secret
  negative-leak (local half) MET; Item B runbook-completeness against §3 row S + §5 MET (all 8 rule-S
  elements present + consistent: CloudWatch datasource, AWS/RDS/DatabaseConnections, DBInstanceIdentifier
  placeholder, pinned Maximum/1-min, `== 0`, `for: 15m`, Default policy → alert-dual-channel, §5 user-owned
  creds referencing Grafana IAM docs); Item C literal-zero-vs-No-Data scope guard MET; Item E 2026-07-27
  deadline surfaced MET; Item D drill-DEFINITION correctness MET (firing DEFERRED-LIVE). All live items
  DEFERRED-LIVE (CW datasource + creds + rule creation + routing + drill firing), NO UNTESTABLE. Report:
  phases/08-seoul-replica-alert/verification.md. One divergence (verify.md `## Divergences`): the AWS-secret
  grep exits 0 (not phase-07's clean exit 1) — adjudicated as false-positive `${{ secrets.* }}` refs via git
  provenance, NOT a leak; no send-back (not a decision/spec conflict).
- capture: both `meme draft --from-phase` invocations ran (task dir + phase-08 dossier dir) — gitignored
  drafts written (stdout not captured; background job produced no output). sweep: watermark advanced to
  ed94e8257c16; ~40 candidates reviewed — NONE made stale/obsolete by this docs-only diff (all are unrelated
  Spring/React/vitest/CloudWatch-agent/latency/git/testing/Redis/Zod lessons; the lone CloudWatch fact is the
  procstat agent-plugin memory lesson, unrelated to a Grafana CW-datasource alert rule). No docs edited, no
  retire proposals. No directory-scoped convention decisions ratified (the rule-S realization decisions —
  Maximum/1-min statistic+period; single-contact-point routing — are meme decision-facts, not directory
  rules) → no CLAUDE.md edits.
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: phases/08-seoul-replica-alert/ledger.md (new), phases/08-seoul-replica-alert/verification.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M — phase-08 entry ONLY; diff verified to touch no
  other entry). NO code (operational-only phase — nothing touches the repo).
  IGNORED: the entire pre-existing baseline-dirty set (.claude/*, backend SSE/planner sources+tests,
  deploy/base/spring-daemonset.yaml, scripts/ops/*, static submodule pointer, requirements.md, phases/03+04
  ledgers, all untracked docs/tasks/* + terraform/* + frontend/*) — NOT this phase's; plan/ (untracked
  task-level artifact); .meme/drafts (gitignored capture output). Dossiers live outside the repo — never staged.
  NOTE — external index mutation unstaged: `backend/docker-compose.local.yml` was ` M` (present,
  worktree-modified, unstaged) in my baseline capture, but appeared as a STAGED deletion (`D `) mid-phase
  and the file is now absent — it changed from a source EXTERNAL to this phase (parallel user/tooling
  activity this session; cf. the verify.md linter edit). NONE of my commands or spawns touched it
  (spec-verifier only read). It rode into the first `git diff --cached`; I ran `git restore --staged
  backend/docker-compose.local.yml` to return it to an unstaged `D` so the phase commit carries ONLY the 3
  phase-08 docs. NOT restored (if this is the user's in-flight deletion, clobbering it would destroy their
  work). Surfaced for the user in the return.
- manifest status: phase 08 → authored (verdict PASS static; live CW-datasource provisioning + rule creation
  + INV6 through-policy drill consent-gated, due 2026-07-27).
- staged: git diff --cached --stat = 3 files — phases/08-seoul-replica-alert/ledger.md (new),
  phases/08-seoul-replica-alert/verification.md (new), docs/tasks/038-metrics-observability/manifest.json (M).
  Matches the STAGED set exactly. Docs-only; no code. Commit is the USER's — proposal returned, not run here.

