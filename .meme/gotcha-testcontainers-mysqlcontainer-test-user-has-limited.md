---
uid: 01KXWHN4FY8N587RM8W60GNJSZ
created: 2026-07-19T06:40:12Z
updated: 2026-07-19T07:25:55Z
scope: global
category: gotcha
origin: draft
title: Testcontainers MySQLContainer 'test' user has limited privileges (only the configured database). To use JDBC createDatab
status: active
attributed_to: llm
entities: [Testcontainers, MySQL]
hash: 39746421
source: text
---

Testcontainers MySQLContainer 'test' user has limited privileges (only the configured database). To use JDBC createDatabaseIfNotExist=true, connect as root (password == container password) and execute: GRANT ALL ON *.* TO 'test'@'%'.
