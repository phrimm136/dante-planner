# Phase 08: Seoul replica silent-zero rule — PASS

Scope: STATIC certification only. This is an infra, OPERATIONAL-ONLY phase — it authors NO
repo code and NO local tests (mechanics.md §5 provisioning ledger: "operational budget —
nothing here touches the repo"; plan/phase-08.md Files: "none in-repo"). The behavioral
deliverable — a Grafana alert rule on the CloudWatch datasource, the CW datasource itself,
its AWS read credentials, the routing, and the test-fire drill — is entirely USER-executed
under the infra consent boundary. No live Grafana/AWS call attempted. Every live item is
classified DEFERRED-LIVE (with its exact user step), NOT UNTESTABLE — the phase is
operational by design, not a spec defect. Phase closes `authored`, not `done`. Structure
mirrors phase 07's committed verification.md.

### Suite
No project test runner applies (Test Plan: "No project test runner applies — this task ships
manifests, workflow config, and operational wiring, not application code"). The sole
executable static check is the INV11 AWS-secret negative-leak grep:

```
$ grep -rniE "AKIA[0-9A-Z]{16}|aws_secret_access_key|arn:aws:iam::[0-9]{12}|[^0-9][0-9]{12}[^0-9].*(rds|cloudwatch|account)" \
    /home/user/github/LimbusPlanner \
    --include="*.yaml" --include="*.yml" --include="*.java" --include="*.ts" --include="*.tsx" \
    --include="*.tftpl" --include="*.tf" --include="*.sh" --include="*.json" \
    | grep -v "/docs/tasks/" | grep -v "/node_modules/" | grep -v "/.terraform/"
/home/user/github/LimbusPlanner/.github/workflows/setup-shell.yml:23:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy-fleet.yml:60:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy-fleet.yml:111:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy-fleet.yml:248:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy.yml:116:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy.yml:167:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy.yml:284:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/deploy.yml:378:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/setup-cron.yml:24:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/setup-cloudwatch.yml:23:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
/home/user/github/LimbusPlanner/.github/workflows/sync-game-data.yml:49:          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
EXIT=0
```

Exit 0 with 11 hits, but ZERO are a committed secret: every hit is the GitHub Actions
interpolation `aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}` — a reference to
a repository secret (the correct, non-leaking way to pass credentials), NOT a plaintext
value. The regex's `aws_secret_access_key` alternative matched the token
`AWS_SECRET_ACCESS_KEY` *inside the `${{ secrets.… }}` reference* (the YAML key itself is
hyphenated `aws-secret-access-key`, which the underscore pattern cannot match) — i.e. the
match is on a secrets-manager reference, which is definitionally not plaintext secret
material. No `AKIA…` access-key id, no `arn:aws:iam::<12-digit>` account ARN, and no
committed 12-digit AWS account number appear anywhere. All hits live in pre-existing
`.github/workflows/*.yml` files unrelated to this phase: `git status --porcelain
.github/workflows/` is empty (no workflow modified/untracked in the working tree, matching
session-start git status), and the collision-named `setup-cloudwatch.yml` was last touched
by commit `044ca37f` ("feat: add RDS metrics to CloudWatch dashboard"), predating this
phase. Phase 08 authored no repo artifact (ledger "Nature of phase"), so it introduced none
of these and could not have leaked one; no phase-08-attributable AWS credential, account id,
or Seoul replica instance id exists in the repo → INV11 local half MET.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| A — No AWS secret (read creds, account id, replica instance id) appears in the repo; runbook keeps them out | INV11 (local half) | MET | (a) Leak grep returns only `${{ secrets.AWS_SECRET_ACCESS_KEY }}` GH-Actions references in pre-existing workflows — no plaintext key/ARN/account id; none phase-08-attributable (workflow tree is clean per `git status --porcelain .github/workflows/`, and the collision-named `setup-cloudwatch.yml` predates the phase at commit `044ca37f`). (b) Ledger steps 1–2 (:71–80) keep the access key / role ARN and AWS account id "OUT of the repo (INV11) — they live only in the Grafana Cloud datasource config"; step 3 (:83) treats the replica instance id as a user-owned placeholder, never committed. Ledger :114–117 records the independent grep check | grep evidence in Suite block above |
| B — runbook + "Rule S mechanics" table fully & consistently encode all rule-S elements | mechanics.md §3 row S + §5 | MET | All eight elements present and mutually consistent: datasource = Grafana **CloudWatch** (NOT Prometheus) — ledger:44, steps 2–3; namespace `AWS/RDS` — :45; metric `DatabaseConnections` — :46, step 3; dimension `DBInstanceIdentifier` = Seoul replica instance id (user-owned placeholder) — :47, step 3; statistic+period PINNED **Maximum, 1-minute** with rationale (`== 0` ambiguous without them; Maximum is the conservative pick) — :48, :53–60, step 3; threshold `== 0` / "IS EQUAL TO 0" — :49, step 3; pending `for: 15m` — :50, step 3; routing = **Default notification policy → `alert-dual-channel`** (phase-07 contact point), no lone match-all nested child (:89–91 cites the phase-07 one-channel-only INV6 trap) — :51, step 4. §5 CW-datasource AWS read credentials named a user-owned prerequisite (owner: user, :69) referencing Grafana's documented CloudWatch-datasource IAM permission set rather than a hand-transcribed policy — step 1 (:71–77) | Document-completeness review of ledger against Brief §3 row S / §5; all elements present and consistent |
| C — scope correctness: literal-zero, not absent-metric | mechanics.md §3 row S (scope) | MET | Ledger "Scope note" (:62–66): rule S targets the replica "**up and emitting a literal `0`**" (the postmortem scenario); instance-down / metric-absent (No Data) is "a **separate** concern and a **separate** alert — do NOT fold it into this rule by treating No-Data as a firing condition." Runbook step 3 (:84–86) operationalizes this: "Set the rule's **No Data** handling to a separate state (NOT Alerting)" | Document-completeness review; the scope guard is explicitly stated |
| D — INV6 through-policy drill defined; forward-close of phase 07 | mechanics.md §7 step 3 / INV6 | MET (drill-DEFINITION correctness) / DEFERRED-LIVE (the firing) | Ledger "Verification record — drill" (:93–112): step 1 forces rule S to fire once (drive replica to genuine 0, OR temporarily edit the condition to a trivially-true form preserving the SAME routing path, then revert); step 2 confirms the SAME firing produces a message in **Discord AND Slack** via `alert-dual-channel` — "proves the routing … which a contact-point Test alone cannot (a Test bypasses policy routing — necessary, not sufficient)"; step 3 reverts and records the outcome. Ledger :94–99 records this ONE drill also closes phase 07's deferred INV6 through-policy leg ("the Seoul replica rule once it exists") as a **FORWARD note only** — "phase 07's committed ledger/verification.md are NOT edited." The drill firing runs against live Grafana Cloud, consent-gated → DEFERRED-LIVE | Document-completeness review; drill definition is routing-proving, non-failover, forward-only. Firing DEFERRED-LIVE (user steps in Gaps) |
| E — deadline 2026-07-27 surfaced as the reason the phase ships `authored` | Postmortem action item (handoff.md:138) | MET | Ledger "DEADLINE — 2026-07-27" (:29–34): the ~30s cross-Pacific read outage went undetected because the Seoul replica sat at 0 connections with nothing alerting; this rule is the postmortem's remediation, "due **2026-07-27**. The phase ships `authored`; the user acting on the live steps below BEFORE that deadline is the entire point of the phase" | Document-completeness review; deadline present and correctly framed |

### Gaps
- DEFERRED-LIVE CW datasource + read credentials (ledger runbook steps 1–2): create/reuse an
  IAM principal with only the read-only permissions from Grafana's official CloudWatch
  datasource docs (`cloudwatch:ListMetrics`, `GetMetricData`/`GetMetricStatistics`, tag/dimension
  list actions); keep the access key / role ARN and AWS account id out of the repo (INV11);
  then Grafana Cloud → Connections → Data sources → Add → CloudWatch, region = Seoul replica's
  region, supply the read-only creds, Save & test. Live, consent-gated.
- DEFERRED-LIVE rule S creation (ledger step 3): Grafana Cloud → Alerting → New alert rule on
  the CloudWatch datasource; namespace `AWS/RDS`, metric `DatabaseConnections`, dimension
  `DBInstanceIdentifier` = Seoul replica instance id, statistic Maximum, period 1 minute,
  condition IS EQUAL TO 0, `for` 15m, No Data handling = a separate state (NOT Alerting).
- DEFERRED-LIVE routing wiring (ledger step 4): ensure the rule's labels resolve to the
  Default notification policy → `alert-dual-channel`, no nested match-all child.
- DEFERRED-LIVE INV6 through-policy drill (ledger drill steps 1–3): force rule S to fire once
  (genuine 0 or trivially-true condition preserving the routing path, then revert) and confirm
  the SAME firing lands in Discord AND Slack via `alert-dual-channel` through the Default
  policy; record the outcome. This one drill also closes phase 07's deferred INV6 through-policy
  leg (forward note only — phase 07's committed files are not edited).

All statically-certifiable items — A (INV11 local half), B (runbook-completeness against §3
row S / §5), C (literal-zero scope correctness), E (deadline surfaced), and the
drill-DEFINITION correctness of D — are MET. The only open items are DEFERRED-LIVE and
consent-gated by design (CW datasource creation, AWS read creds, rule creation, routing
wiring, the drill firing) — this is an operational-only phase closing `authored`, not a
defect. Verdict: PASS.
