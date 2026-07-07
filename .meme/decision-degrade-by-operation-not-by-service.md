---
uid: 01KWVVAJG1S5XMB6HWYNG8ZYK0
created: 2026-07-06T13:54:15Z
updated: 2026-07-06T13:56:30Z
scope: global
category: decision
origin: draft
title: Degrade by operation, not by service: reads must survive write-path outages; security reads with bounded blast radius fa
status: active
attributed_to: llm
entities: [TokenBlacklistService, Redis, deployment]
hash: 4042194f
source: text
---

Degrade by operation, not by service: reads must survive write-path outages; security reads with bounded blast radius fail open with a loud metric rather than failing the request; write outages surface as typed, user-explainable errors with client-side state preserved.
