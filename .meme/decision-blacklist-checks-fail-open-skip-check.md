---
uid: 01KX5XA7H3PWB8HTX8X4TSS44V
created: 2026-07-10T11:41:25Z
updated: 2026-07-10T12:07:04Z
scope: LimbusPlanner
category: decision
origin: draft
title: Blacklist checks fail-open (skip check + alert on Redis unavailable) rather than fail-closed. Consistent with risk postu
status: active
attributed_to: llm
entities: []
hash: 75f3fe7d
source: /home/user/github/LimbusPlanner/docs/tasks/034-multi-region-k8s-architecture
---

Blacklist checks fail-open (skip check + alert on Redis unavailable) rather than fail-closed. Consistent with risk posture; auth TTL (15-min access-token) bounds hazard window. Bounded blast radius justifies open failure over cascading auth outage.
