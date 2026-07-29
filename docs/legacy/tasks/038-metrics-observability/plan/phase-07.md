# Phase 07: Alert delivery pipeline

- Kind: infra (Grafana Cloud provisioning; apply is consent-gated — created manually in Grafana Cloud
  by the user per the provisioning ledger; drill-verified)
- Files: none in-repo (operational — Grafana Cloud contact points + notification policy; §5)
- Tests: none local; verification is drill INV6
- External contract: one Grafana notification policy with two contact points — Discord webhook
  (primary) and Slack incoming webhook (fallback); a firing alert reaches BOTH channels.
- Mechanics sections: `mechanics.md` §3 (Delivery contract); `mechanics.md` §5 (webhook provisioning);
  `mechanics.md` §7 step 3 (notification drill).
- Implementation methods: none in-repo.
- Considerations:
  - `requirements.md` Decisions (taste — dual channel): a single webhook channel is itself a silent
    point of failure (revoked webhook / provider outage drops firing alerts with no symptom), so the
    primary is always backed by an independent second channel.
  - `mechanics.md` §5: Discord + Slack webhook URLs (owner: user) live ONLY in Grafana Cloud contact
    points, never in the repo (INV11). Needed before any step-3 alert wiring.
  - Scope boundary (`requirements.md` Decisions): CW-native alarms (billing, EC2 auto-recovery) stay
    SNS→email and are NOT rerouted here; no SNS→Lambda→Discord shim is built.
- Depends on: none
- Verify: drill INV6 — Grafana contact-point Test on both channels, then one real drill-fired rule;
  a message lands in Discord AND Slack. Record in the phase verification doc.
