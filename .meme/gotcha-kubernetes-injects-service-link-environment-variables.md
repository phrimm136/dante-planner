---
uid: 01KXBE889E2S9RFWYK9C4VZVFV
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:13Z
scope: global
category: gotcha
origin: draft
title: Kubernetes injects service-link environment variables for every Service in the namespace, including <SERVICE>_PORT=tcp:/
status: active
attributed_to: llm
entities: [Kubernetes]
hash: b529f062
source: text
---

Kubernetes injects service-link environment variables for every Service in the namespace, including <SERVICE>_PORT=tcp://ip:port (a URL string). These collide with application configuration keys using the same naming pattern — e.g., REDIS_RATELIMIT_PORT clobbering Spring's redis.rate-limit.port integer causes NumberFormatException at binding. Fix: set enableServiceLinks: false on the pod spec to disable service-link injection.
