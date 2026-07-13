---
uid: 01KXBE88AMJ99GGSM4VE9HZQKC
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:12Z
scope: global
category: gotcha
origin: draft
title: ArgoCD repo-server recursively clones git submodules when rendering manifests; a private SSH-URL submodule (git@github.c
status: active
attributed_to: llm
entities: [ArgoCD, Git]
hash: b10c7d13
source: text
---

ArgoCD repo-server recursively clones git submodules when rendering manifests; a private SSH-URL submodule (git@github.com:...) fails with 'Permission denied (publickey)' and surfaces as a ComparisonError. Fix: set ARGOCD_GIT_MODULES_ENABLED=false on the repo-server deployment when the overlay does not use submodules.
