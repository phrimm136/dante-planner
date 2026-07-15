# Phase 07 Scenario Ledger: Alert delivery pipeline

Dossiers: ~/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-07/
Baseline: HEAD b34430a1. Working tree carries a heavy PRE-EXISTING dirty set unrelated to this phase
— NOT this phase's to stage: `.claude/*` (harness/skills/settings/commands), backend SSE + planner
sources/tests (PlannerCommandService/PlannerSyncEventService/SseEnvelope/SsePublisher/
SseRedisSubscriber + their tests, new PlannerSyncEventServiceTest), deploy/base/spring-daemonset.yaml,
scripts/ops/lib/*, static submodule pointer, requirements.md (M), phases/03 + phases/04 ledgers (M),
and every untracked docs/tasks/* + terraform/* + frontend/* path. manifest.json was CLEAN at open
(committed) → my phase-07 edit is cleanly attributable. plan/ is an untracked task-level artifact — NOT
this phase's to stage (phase-04 set the precedent). Phase-07 delta at open = none.

## Nature of phase — operational-only, no repo artifacts
Kind: infra (consent-gated apply). `plan/phase-07.md`: "Files: none in-repo (operational)"; `mechanics.md`
§5 provisioning ledger is titled "operational budget — nothing here touches the repo". There is NO
production/test code to author, NO local test suite, and NO static repo artifact to render-check. The
entire deliverable lives in Grafana Cloud (contact points + one notification policy) and is created by the
USER under the infra consent boundary. Accordingly:
- tdd-red / tdd-green / tdd-refactor burndown is INAPPLICABLE — no assertions exist to drive red, no code
  to write, no minimum-code debt to consolidate.
- The pipeline collapses to: author this ledger (operational runbook + INV6 drill definition) → spec-verifier
  static certification (INV11 negative-leak + runbook-completeness; every live item DEFERRED-LIVE) → capture
  → docs-only staged commit (ledger + verification.md + manifest.json) → phase closes `authored`, not `done`
  (all live steps + the INV6 drill are the user's, consent-gated).

## External contract (plan/phase-07.md)
One Grafana notification policy with two contact points — Discord webhook (primary) and Slack incoming
webhook (fallback); a firing alert reaches BOTH channels.

## Delivery contract (mechanics.md §3)
One notification policy; contact points Discord webhook (primary) and Slack incoming webhook (fallback).
Taste rationale (requirements.md Decisions): a single webhook channel is itself a silent point of failure —
a revoked webhook or provider outage drops firing alerts with no symptom — so the primary is always backed
by an independent second channel. Scope boundary: CW-native alarms (billing, EC2 auto-recovery) stay
SNS→email and are NOT rerouted here; no SNS→Lambda→Discord shim is built.

### LOAD-BEARING INVARIANT — redundancy, NOT failover
INV6 and §3 require a firing alert reach Discord **AND** Slack on **every** firing — both channels every
time. "Primary/fallback" here means an independent redundant backup channel (so a silent delivery failure on
one still delivers on the other), NOT Grafana conditional/escalation failover (Slack only when Discord
fails). A failover configuration would FAIL INV6. The runbook below routes every alert to both contact points.

## Operational runbook (USER-executed, consent-gated) — INV11: webhook URLs live ONLY in Grafana Cloud, never in the repo
Owner of both webhook URLs: user (mechanics.md §5). Exact steps:

1. **Create the Discord webhook.** In the target Discord server → channel → Edit Channel → Integrations →
   Webhooks → New Webhook; name it (e.g. `grafana-alerts`), copy the webhook URL
   (`https://discord.com/api/webhooks/...`). Keep it out of the repo (INV11).
2. **Create the Slack incoming webhook.** In the Slack workspace → an app with Incoming Webhooks enabled →
   Add New Webhook to Workspace → pick the target channel; copy the webhook URL
   (`https://hooks.slack.com/services/...`). Keep it out of the repo (INV11).
3. **Create ONE contact point holding BOTH integrations.** Grafana Cloud → Alerting → Contact points → Add
   contact point; name `alert-dual-channel`. Add integration **Discord** → paste the Discord webhook URL
   (the primary channel). In the SAME contact point, **Add integration** again → **Slack** → paste the Slack
   incoming-webhook URL (the redundant fallback channel). Save. Grafana fires ALL integrations in a contact
   point on every notification, so this one contact point reaches Discord AND Slack on every firing.
4. **Point the notification policy at it.** Grafana Cloud → Alerting → Notification policies → set the
   **Default policy** contact point to `alert-dual-channel`. No nested routes are needed. Every firing routes
   to the default policy → both integrations fire → Discord AND Slack, every time.

   **Realization decision (deviates from the plan's literal "two contact points"; INV6 governs).** The plan/§3
   phrase "two contact points (Discord primary / Slack fallback)" is an implementation sketch; the load-bearing
   behavioral requirement is INV6 — a firing reaches BOTH channels every time. The single-contact-point /
   two-integrations form above satisfies that under any reading and is trap-free. Do NOT instead use one
   default policy + one match-all nested child: a nested policy that matches an alert **redirects** it to the
   child's contact point and does NOT also fire the parent default (and "Continue matching subsequent sibling
   nodes" continues to SIBLINGS, of which there are none), so a single match-all child would deliver Slack-only
   and FAIL INV6. If two separate contact points are strictly required, the ONLY correct policy shape is TWO
   sibling match-all child routes (continue=on on the first, off on the second), each → one contact point —
   never one-default + one-child. This is redundancy, never Grafana escalation/failover.

## Verification record — INV6 notification drill (mechanics.md §7 step 3)
DEFERRED-LIVE — the drill is user-executed against live Grafana Cloud, consent-gated. Steps:
1. Grafana Cloud → Contact points → **Test** on `alert-dual-channel`; confirm the test message lands in BOTH
   Discord AND Slack (a contact-point Test fires all its integrations at once — this proves both webhooks
   deliver, but it BYPASSES the notification policy routing, so it is necessary, not sufficient, for INV6).
2. Drill-fire ONE real rule (a temporary/scratch Grafana-managed rule with a trivially-true expression, or
   the Seoul replica rule once it exists). Because it fires through the notification policy, this proves the
   ROUTING (step 4 above) actually delivers — confirm the SAME firing produces a message in Discord AND Slack.
3. Delete the scratch rule; record the outcome (both channels received the same firing via the policy) in this
   phase's verification record and mark Done-When "delivery path drill-fired once via contact-point test (INV6)".
Definition of done for INV6: a single firing reaches both channels — not merely two independent contact-point
tests. (Rule-evaluation drills for the alert rules themselves are deferred to step-3 remote_write wiring;
INV6 needs only one firing through the delivery path.)

## Acceptance
- No local acceptance test exists (operational-only phase). Behavioral acceptance = the INV6 live drill
  (DEFERRED-LIVE, user-executed). Static acceptance = spec-verifier certifies INV11 negative-leak +
  runbook-completeness against §3/§5.

## Scenarios
| # | Scenario | Dossier | Status | Red proof | Green proof |
|---|----------|---------|--------|-----------|-------------|
| — | none — operational-only phase, no testable code (see "Nature of phase") | — | n/a | n/a | n/a |

## List Revisions
- none — no burndown.

## Pipeline (post-burndown)
- burndown: not applicable (operational-only; no code, no local tests).
- refactor: not applicable (no authored code to consolidate).
- scoped "suite" (run by me, the phase's independent check — no unit suite exists): INV11 leak grep over
  repo (excluding docs/tasks) for `discord(app)?\.com/api/webhooks` + `hooks\.slack\.com/services` across
  yaml/yml/java/ts/tsx/tftpl/tf/sh/json → grep exit 1 (NO matches) = no webhook URL committed. GREEN.
- verify: PASS after 1 round — spec-verifier certified the static scope: INV11 negative-leak (repo-wide
  webhook-URL grep, exit 1 / no matches) MET; runbook-completeness against §3/§5 (all four sub-clauses)
  MET; INV6 drill-definition (redundancy not failover) MET. All live items DEFERRED-LIVE (contact-point +
  policy creation; INV6 drill). No UNTESTABLE. Report: phases/07-alert-delivery/verification.md. No divergences.
- capture: drafts 25 from task dir + 3 from dossiers (all gitignored, indexed for dedup — the 3 dossier
  drafts distil the phase-07 residue: authored-not-done status, the two valid runbook realizations, and the
  "fallback = redundant second channel, not failover" gotcha). sweep: watermark advanced to b34430a1;
  ~40 candidates reviewed — NONE made stale/obsolete by this docs-only diff (all surfaced facts are
  unrelated Spring/React/vitest/CloudWatch/latency/git/testing lessons). No docs edited, no retire proposals.
  No directory-scoped convention decisions ratified (runbook decisions are meme decision-facts, not
  directory rules) → no CLAUDE.md edits.
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: phases/07-alert-delivery/ledger.md (new), phases/07-alert-delivery/verification.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M — phase-07 entry ONLY; diff verified to touch no
  other entry). NO code (operational-only phase — nothing touches the repo).
  IGNORED: the entire pre-existing baseline-dirty set (.claude/*, backend SSE/planner sources+tests,
  deploy/base/spring-daemonset.yaml, scripts/ops/*, static submodule pointer, requirements.md, phases/03+04
  ledgers, all untracked docs/tasks/* + terraform/* + frontend/*) — NOT this phase's; plan/ (untracked
  task-level artifact); .meme/drafts (gitignored capture output). Dossiers live outside the repo — never staged.
- manifest status: phase 07 → authored (verdict PASS static; live provisioning + INV6 drill consent-gated).
- staged: git diff --cached --stat = 3 files — phases/07-alert-delivery/ledger.md (new),
  phases/07-alert-delivery/verification.md (new), docs/tasks/038-metrics-observability/manifest.json (M).
  Matches the STAGED set exactly. Docs-only; no code. Commit is the USER's — proposal returned, not run here.
