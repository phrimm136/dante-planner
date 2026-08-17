# Phase 08: Seoul replica silent-zero rule

- Kind: infra (Grafana Cloud provisioning; consent-gated; drill/deadline-verified)
- Files: none in-repo (operational — Grafana CW-datasource rule; §5)
- Tests: none local
- External contract: a Grafana alert rule on the CloudWatch datasource firing when the Seoul RDS
  replica reports `DatabaseConnections == 0` sustained 15m, routed to the phase-07 notification
  policy (Discord primary / Slack fallback).
- Mechanics sections: `mechanics.md` §3 (row S + delivery contract); `mechanics.md` §5 (CW-datasource
  AWS read credentials).
- Implementation methods: none in-repo.
- Considerations:
  - `requirements.md` Decisions (deadline-bound Seoul rule): born as a Grafana CW-datasource rule,
    Discord-native — the CW datasource needs only the Grafana stack + read-only AWS credentials, NOT
    remote_write (`handoff.md:138-139`). This is why it can ship ahead of the remote_write-blocked
    rules in phase 09.
  - Deadline 2026-07-27 (postmortem action item, `handoff.md:138`); the `~30s` cross-Pacific read
    outage went undetected because this replica sat at 0 connections with nothing alerting.
  - `mechanics.md` §5: CW-datasource AWS read credentials (owner: user) required before this rule.
- Depends on: 07 (notification policy + contact points)
- Verify: rule exists in Grafana Cloud on the CW datasource, routed to the dual-channel policy;
  test-fire confirms delivery. Record in the phase verification doc.
