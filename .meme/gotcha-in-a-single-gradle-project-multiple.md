---
uid: 01KXWM100KWSBZRVVSB31Y5MHE
created: 2026-07-19T07:21:38Z
updated: 2026-07-19T07:25:56Z
scope: global
category: gotcha
origin: draft
title: In a single Gradle project, multiple Test tasks run sequentially, not concurrently. Splitting expensive tests into a sep
status: active
attributed_to: llm
entities: [Gradle]
hash: fdba3fa4
source: text
---

In a single Gradle project, multiple Test tasks run sequentially, not concurrently. Splitting expensive tests into a separate Test task would not parallelize; the task would wait for other tasks to complete, causing wall-clock regression.
