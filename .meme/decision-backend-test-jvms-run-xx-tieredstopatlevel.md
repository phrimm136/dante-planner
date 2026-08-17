---
uid: 01KXK6EYMF5W1E0RTNNVATXG4V
created: 2026-07-15T15:31:25Z
updated: 2026-07-19T07:25:55Z
scope: LimbusPlanner
category: decision
origin: draft
title: Backend test JVMs run -XX:TieredStopAtLevel=1 -XX:+UseParallelGC. Test forks are short-lived with startup-dominated over
status: active
attributed_to: llm
entities: [JVM, Spring Boot]
hash: b7ed7383
source: text
---

Backend test JVMs run -XX:TieredStopAtLevel=1 -XX:+UseParallelGC. Test forks are short-lived with startup-dominated overhead (Spring context initialization), so C2 JIT never reaches payoff; these flags must never be applied to production or bootRun configs.
