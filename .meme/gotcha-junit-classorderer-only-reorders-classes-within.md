---
uid: 01KXWM0ZZP3CA9VYWPG147PNNM
created: 2026-07-19T07:21:38Z
updated: 2026-07-19T07:25:56Z
scope: global
category: gotcha
origin: draft
title: JUnit ClassOrderer only reorders classes within each Gradle fork's assigned slice, not across forks, so it cannot reliab
status: active
attributed_to: llm
entities: [JUnit, Gradle]
hash: 2d27a0c6
source: text
---

JUnit ClassOrderer only reorders classes within each Gradle fork's assigned slice, not across forks, so it cannot reliably make one specific heavy class start first globally across all parallel forks.
