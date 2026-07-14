---
uid: 01KX5X71YRADZ96QVYMJJV47ZB
created: 2026-07-10T11:39:41Z
updated: 2026-07-10T12:07:04Z
scope: LimbusPlanner
category: gotcha
origin: draft
title: No timing constants (fixed delays, sleep calls, assumed durations) are permitted in correctness paths — every 'has X hap
status: active
attributed_to: llm
entities: [INV4, awaitCaughtUp, ReplicationControl]
hash: ede555b9
source: /home/user/github/LimbusPlanner/docs/tasks/034-multi-region-k8s-architecture
---

No timing constants (fixed delays, sleep calls, assumed durations) are permitted in correctness paths — every 'has X happened' check must be a concrete, verifiable condition, never a timer-based assumption. This is a system-wide invariant enforced across all phases.
