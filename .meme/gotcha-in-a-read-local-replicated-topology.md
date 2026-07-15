---
uid: 01KXHVBGHM6XR34ZSMVFTW2F9J
created: 2026-07-15T02:58:03Z
updated: 2026-07-15T03:02:56Z
scope: global
category: gotcha
origin: draft
title: In a read-local replicated topology, observability must be instrumented on every read-serving instance, not only on the
status: active
attributed_to: llm
entities: [read-local, replica, observability]
hash: 12cc340c
source: text
---

In a read-local replicated topology, observability must be instrumented on every read-serving instance, not only on the primary. Primary-only telemetry (perf_schema digests, slow-query counters, QPS) structurally cannot detect pathologies that occur exclusively on replicas — replica-only issues remain invisible until they propagate to the primary.
