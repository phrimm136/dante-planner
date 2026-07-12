---
uid: 01KXBE88BBY28W6BJMG821357P
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:12Z
scope: global
category: gotcha
origin: draft
title: ArgoCD reports SYNC=Unknown as a lossy aggregation of distinct failure causes (AppProject missing, targetRevision wrong,
status: active
attributed_to: llm
entities: [ArgoCD]
hash: 2382cf86
source: text
---

ArgoCD reports SYNC=Unknown as a lossy aggregation of distinct failure causes (AppProject missing, targetRevision wrong, path not found, kustomize build failure). Only the first failed gate is reported. Debug the true cause in .status.conditions, not the lossy SYNC word. 'Synced' means the live cluster state matches the pushed commit on the tracked branch, not the user's latest local commit.
