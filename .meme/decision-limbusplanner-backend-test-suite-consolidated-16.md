---
uid: 01KXWHN4H5H2ARK07Z09XRQ5D3
created: 2026-07-19T06:40:12Z
updated: 2026-07-19T07:25:55Z
scope: LimbusPlanner
category: decision
origin: draft
title: LimbusPlanner backend test suite consolidated 16 single-MySQL @Container integration tests into SharedMySqlContainerSupp
status: active
attributed_to: llm
entities: [SharedMySqlContainerSupport]
hash: a1f0193c
source: text
---

LimbusPlanner backend test suite consolidated 16 single-MySQL @Container integration tests into SharedMySqlContainerSupport with per-subclass databases (JDBC createDatabaseIfNotExist) instead of per-transaction rollback, because 11 of 16 tests commit and would bleed data. Each subclass must override @DynamicPropertySource (not inherit) to preserve per-subclass context cache.
