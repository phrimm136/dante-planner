---
uid: 01KXWHN4HTCS6Z9KMTHV0Z96EB
created: 2026-07-19T06:40:12Z
updated: 2026-07-19T07:25:56Z
scope: LimbusPlanner
category: decision
origin: draft
title: LimbusPlanner uses per-fork singleton MySQLContainer (starts once per Gradle fork, dies at fork end) instead of Testcont
status: active
attributed_to: llm
entities: [SharedMySqlContainerSupport]
hash: 40c14c1e
source: text
---

LimbusPlanner uses per-fork singleton MySQLContainer (starts once per Gradle fork, dies at fork end) instead of Testcontainers.withReuse() to avoid persistent RAM tax on a 13GB dev box running parallel services (kind cluster, freshrss). Measured improvement: testcontainers p50 latency 56→23ms, peak 75→47ms.
