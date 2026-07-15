---
uid: 01KXHVAM911SB2QEVT8R85ZZHN
created: 2026-07-15T02:57:35Z
updated: 2026-07-15T03:02:55Z
scope: LimbusPlanner
category: gotcha
origin: draft
title: Webhook contact point failures in Grafana alert routing are silent — no error or symptom indicates when a webhook fails
status: active
attributed_to: llm
entities: [Grafana, prometheus]
hash: 8e214279
source: text
---

Webhook contact point failures in Grafana alert routing are silent — no error or symptom indicates when a webhook fails (revoked, provider outage), so alerts can stop firing without detection. Mitigate by routing to independent backup channels (Discord + Slack on the same notification policy).
