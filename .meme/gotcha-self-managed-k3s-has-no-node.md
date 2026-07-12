---
uid: 01KXBE88EPP2ES4D0HMHYPGKRH
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:12Z
scope: global
category: gotcha
origin: draft
title: Self-managed k3s has no node garbage collection (unlike EKS with cloud-controller-manager). When a node is terminated, i
status: active
attributed_to: llm
entities: [k3s, AWS]
hash: 83fd8d25
source: text
---

Self-managed k3s has no node garbage collection (unlike EKS with cloud-controller-manager). When a node is terminated, its Kubernetes Node object lingers as NotReady indefinitely. Fix options: deploy the AWS cloud-controller-manager (provides auto-GC and topology labels) or configure an ASG termination lifecycle hook to run kubectl delete node on the terminating node.
