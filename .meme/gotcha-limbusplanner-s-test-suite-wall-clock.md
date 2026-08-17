---
uid: 01KXWM1016S635GXKZVEF48KNB
created: 2026-07-19T07:21:38Z
updated: 2026-07-19T07:25:56Z
scope: LimbusPlanner
category: gotcha
origin: draft
title: LimbusPlanner's test suite wall-clock floor (~412s) is driven by RedisConnectionRecoveryIT and replication tests schedul
status: active
attributed_to: llm
entities: [RedisConnectionRecoveryIT, DegradationIT]
hash: 9c5a37f1
source: text
---

LimbusPlanner's test suite wall-clock floor (~412s) is driven by RedisConnectionRecoveryIT and replication tests scheduled late in the fork queue (~252s start) with inherent ~150s duration (2-MySQL boot + 30s recovery waits). This scheduling-driven tail cannot be reduced without increasing fork concurrency, allocating more RAM, or changing recovery-test timing semantics.
