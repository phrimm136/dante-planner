---
uid: 01KXNTGNH94PYZQ4DJ1GN1PYMW
created: 2026-07-16T16:00:22Z
updated: 2026-07-16T16:42:51Z
scope: global
category: gotcha
origin: draft
title: Grafana Cloud Loki rejects log pushes (401 'authentication error: invalid scope requested') when the access-policy token
status: active
attributed_to: llm
entities: [Grafana Cloud Loki, Alloy]
hash: 1c530a29
source: text
---

Grafana Cloud Loki rejects log pushes (401 'authentication error: invalid scope requested') when the access-policy token lacks the logs:write scope. Provisioning chain validation (pod Running, ExternalSecret synced, config loading) does NOT validate token scope — the whole system appears green while Loki silently rejects 100% of pushes. Validate token scope with a test push before storing it in Secrets Manager.
