---
uid: 01KXWHN4JC7V2PY5P001NCDZ24
created: 2026-07-19T06:40:12Z
updated: 2026-07-19T07:25:56Z
scope: LimbusPlanner
category: gotcha
origin: draft
title: LimbusPlanner replication-pair test classes (CausalHarnessSupport subclasses, DegradationIT, RedisConnectionRecoveryIT)
status: active
attributed_to: llm
entities: [SharedMySqlContainerSupport]
hash: 4158ecc9
source: text
---

LimbusPlanner replication-pair test classes (CausalHarnessSupport subclasses, DegradationIT, RedisConnectionRecoveryIT) were not consolidated into SharedMySqlContainerSupport because they require a primary+replica GTID pair — an architectural floor incompatible with the single-container base.
