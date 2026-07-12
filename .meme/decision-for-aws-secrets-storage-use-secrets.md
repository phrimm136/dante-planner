---
uid: 01KXBE88DTCMRSMK10Q0GPBPE3
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:13Z
scope: global
category: decision
origin: draft
title: For AWS secrets storage, use Secrets Manager for application runtime secrets that require cross-region replication; use
status: active
attributed_to: llm
entities: [AWS]
hash: f55f65c5
source: text
---

For AWS secrets storage, use Secrets Manager for application runtime secrets that require cross-region replication; use Parameter Store for infrastructure/bootstrap secrets with region-local scope (e.g., kubelet join tokens in node user-data, RDS replication passwords read before pod-level secret injection).
