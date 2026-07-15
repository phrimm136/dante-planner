# Phase 07: Alert delivery pipeline — PASS

Scope: STATIC certification only. This is an infra, OPERATIONAL-ONLY phase — it authors
NO repo code and NO local tests (mechanics.md §5 provisioning ledger: "operational budget
— nothing here touches the repo"). The entire behavioral deliverable (the Grafana Cloud
contact point + the notification policy + the INV6 drill) is USER-executed under the infra
consent boundary. No live Grafana call attempted. Every live item is classified
DEFERRED-LIVE (with its exact user step), NOT UNTESTABLE — the phase is operational by
design, not a spec defect. Phase closes `authored`, not `done`.

This report re-certifies the CORRECTED delivery mechanism. The prior version of this report
certified a retracted shape (one Default policy contact point + one match-all nested child
route with continue=on). That shape is a Grafana routing bug — a nested policy that matches
an alert REDIRECTS it to the child's contact point and does NOT also fire the parent default
("Continue matching subsequent sibling nodes" continues to siblings, of which a lone child
has none), so it would deliver Slack-only and FAIL INV6. The ledger's corrected runbook, and
this report, certify the trap-free shape instead: ONE contact point holding two integrations.

### Suite
No project test runner applies (Test Plan: "No project test runner applies"). The sole
executable static check is the INV11 negative-leak grep:

```
$ grep -rniE "discord(app)?\.com/api/webhooks|hooks\.slack\.com/services" \
    /home/user/github/LimbusPlanner \
    --include="*.yaml" --include="*.yml" --include="*.java" --include="*.ts" \
    --include="*.tsx" --include="*.tftpl" --include="*.tf" --include="*.sh" --include="*.json" \
    | grep -v "/docs/tasks/" | grep -v "/node_modules/"
EXIT=1
```
No matches (grep exit 1). No committed Discord/Slack webhook URL anywhere in the repo
across yaml/yml/java/ts/tsx/tftpl/tf/sh/json (excluding docs/tasks and node_modules).
Phase-07 introduced no repo file, so it could not have leaked one; the repo-wide state is
certified clean → INV11 local half MET.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| No secret (webhook URL, DSN, token, account ID) ever appears in the repo — repo-wide grep before commit | INV11 (local half) | MET | Repo-wide grep for `discord(app)?\.com/api/webhooks` + `hooks\.slack\.com/services` over the 9 tracked extensions returns zero matches (exit 1). Phase-07 authored no repo artifact (ledger "Nature of phase"); the corrected runbook keeps both URLs out of the repo (ledger steps 1–2: "Keep it out of the repo (INV11)") and only inside the Grafana Cloud contact point (steps 3–4) | grep evidence in Suite block above |
| Runbook fully encodes the delivery contract: URLs user-owned & Grafana-only; one contact point `alert-dual-channel` with a Discord integration (primary) + a Slack integration (fallback); ONE policy routing EVERY firing to BOTH channels (redundancy, not failover); CW-native scope boundary preserved | mechanics.md §3 + §5 (delivery contract + provisioning ledger) | MET | (a) Ledger:44 "Owner of both webhook URLs: user (mechanics.md §5)"; steps 1–2 create the Discord + Slack webhook URLs and keep them out of the repo; steps 3–4 paste them ONLY into the Grafana contact point. (b) Step 3 creates ONE contact point `alert-dual-channel` with integration **Discord** (primary channel) + a second integration **Slack** (redundant fallback channel) in the SAME contact point; "Grafana fires ALL integrations in a contact point on every notification, so this one contact point reaches Discord AND Slack on every firing." (c) Step 4: **Default policy** contact point = `alert-dual-channel`, NO nested routes — "Every firing routes to the default policy → both integrations fire → Discord AND Slack, every time." The LOAD-BEARING INVARIANT callout (ledger:37–41) binds this to redundancy on EVERY firing, NOT Grafana escalation/failover. (d) Delivery contract (ledger:34–35): "CW-native alarms (billing, EC2 auto-recovery) stay SNS→email and are NOT rerouted here; no SNS→Lambda→Discord shim is built." — scope boundary preserved | Document-completeness review of ledger against Brief §3/§5; all four sub-clauses present and mutually consistent |
| Realization deviates from the plan's literal "two contact points" — one contact point / two integrations instead — accepted phase-manager ruling, not a defect | plan/phase-07.md + mechanics.md §3 (contact-point count) | MET (accepted deviation-from-plan) | Ledger step-4 "Realization decision" note: the plan/§3 phrase "two contact points (Discord primary / Slack fallback)" is an implementation sketch; the load-bearing behavioral requirement is INV6 (a firing reaches BOTH channels every time). The single-contact-point / two-integrations form satisfies INV6 under any reading and is trap-free. INV6 governs — the deviation is intentional and accepted, not a gap. The note also documents WHY the nested-child alternative is a Grafana routing bug (redirects to child, parent default never fires → Slack-only → FAILS INV6) and that the only correct two-contact-point shape would be two sibling match-all child routes (continue=on then off) — never one-default + one-child | Document-completeness review; deviation is ruled accepted per Brief 2 |
| INV6 drill defined as: contact-point Test on `alert-dual-channel` (fires both integrations, bypasses routing — necessary not sufficient) + one real policy-routed drill-fired rule whose single firing lands in Discord AND Slack | mechanics.md §7 step 3 / INV6 | MET | Ledger "Verification record — INV6 notification drill" (:72–84): step 1 Test `alert-dual-channel` → confirm the test message lands in BOTH Discord AND Slack (a contact-point Test fires all its integrations at once — proves both webhooks deliver, but BYPASSES the notification policy routing → necessary, not sufficient); step 2 drill-fire ONE real rule (scratch trivially-true expression, or the Seoul replica rule) so it fires THROUGH the notification policy, confirming the SAME firing produces a message in Discord AND Slack (proves the routing actually delivers); step 3 delete the scratch rule + record the outcome. Explicit: "Definition of done for INV6: a single firing reaches both channels — not merely two independent contact-point tests." Matches §7 step 3 intent | Document-completeness review; drill definition is correct, routing-proving, and non-failover |
| Delivery path drill-fired once via contact-point test (INV6) — the behavioral proof | Done-When (infra item, phase-07 portion) | DEFERRED-LIVE | Static half (corrected runbook + drill definition) is MET above; the firing itself runs against live Grafana Cloud, consent-gated | See DEFERRED-LIVE user steps below |
| Alert-routing topology exists, every firing → Discord AND Slack | Done-When (routing is phase-07's; the rules are phases 08/09) | MET (topology definition) / DEFERRED-LIVE (live wiring) | Routing topology is fully specified by corrected runbook step 4 (Default policy → `alert-dual-channel`, no nested routes; both integrations fire on every firing). The rules that feed it are out of phase-07 scope (phases 08/09). Live creation of the contact point + policy is consent-gated | Static topology certified; live apply DEFERRED-LIVE below |

### Gaps
- DEFERRED-LIVE INV6 drill (behavioral proof "a message lands in Discord AND Slack"):
  cannot be certified statically — runs against live Grafana Cloud, consent-gated. User
  steps (ledger drill record): (1) Grafana Cloud → Contact points → **Test** on
  `alert-dual-channel` and confirm the test message lands in BOTH Discord AND Slack (fires
  both integrations at once — necessary but not sufficient, it bypasses policy routing);
  (2) drill-fire ONE real Grafana-managed rule (a scratch trivially-true expression, or the
  Seoul replica rule once it exists) so it fires THROUGH the notification policy, and confirm
  the SAME firing produces a message in Discord AND Slack; (3) delete the scratch rule and
  record "both channels received the same firing via the policy", marking Done-When "delivery
  path drill-fired once via contact-point test (INV6)".
- DEFERRED-LIVE contact-point & notification-policy creation (webhooks + the one contact
  point + the policy): live, user-owned, consent-gated. User steps (ledger runbook 1–4):
  create the Discord webhook and the Slack incoming webhook (keep both URLs out of the repo,
  INV11); add ONE contact point `alert-dual-channel` with integration **Discord** (paste the
  Discord webhook URL) plus a second integration **Slack** (paste the Slack incoming-webhook
  URL) in the same contact point; set the **Default policy** contact point to
  `alert-dual-channel` with no nested routes, so every firing reaches both channels.

All statically-certifiable items — INV11 local half; runbook-completeness against §3/§5
(now certifying the corrected one-contact-point / two-integrations shape); the accepted
deviation-from-plan; INV6 drill-definition correctness — are MET. The only open items are
DEFERRED-LIVE and consent-gated by design — this is an operational-only phase closing
`authored`, not a defect. Verdict: PASS.
