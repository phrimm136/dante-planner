---
uid: 01KWVV9WQQ666GRW6CEBRE4PSZ
created: 2026-07-06T13:53:53Z
updated: 2026-07-06T13:56:10Z
scope: LimbusPlanner
category: decision
origin: draft
title: Failover strategy: use RDS Multi-AZ (synchronous same-site) for automatic failover; cross-region authority promotion is
status: active
attributed_to: llm
entities: [RDS, Redis, deployment]
hash: b2870e4d
source: text
---

Failover strategy: use RDS Multi-AZ (synchronous same-site) for automatic failover; cross-region authority promotion is always manual via runbooks, never autonomous. Rationale: automatic failover of a stateful primary without quorum machinery is unsafe; quorum justifies only at scale. Every cross-region authority transition is a deliberate act.
