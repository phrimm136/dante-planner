---
uid: 01KXK6EYKFJTDDQV58DSDTCT77
created: 2026-07-15T15:31:25Z
updated: 2026-07-19T07:25:54Z
scope: LimbusPlanner
category: decision
origin: draft
title: Backend test MySQLContainer definitions (CausalHarnessSupport, DegradationIT, RedisConnectionRecoveryIT, MySQLIntegratio
status: active
attributed_to: llm
entities: [MySQL, Testcontainers, GTID]
hash: d4d50146
source: text
---

Backend test MySQLContainer definitions (CausalHarnessSupport, DegradationIT, RedisConnectionRecoveryIT, MySQLIntegrationTest, HibernateInsertBatchingIT, PlannerQueryCountTest) run with --innodb-flush-log-at-trx-commit=0 --sync-binlog=0 --performance-schema=OFF --skip-name-resolve. Safe because no backend test queries performance_schema and GTID replication is independent of fsync timing or performance_schema. tmpfs rejected due to concurrent container load (up to 13 running) and RAM constraint (13GB).
