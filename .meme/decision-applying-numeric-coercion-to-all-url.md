---
uid: 01KVPVPMW1NG8MTKFHEDQXW300
created: 2026-06-22T05:08:57Z
updated: 2026-07-10T06:39:59Z
scope: LimbusPlanner
category: decision
origin: v1:LimbusPlanner/decision-applying-numeric-coercion-to-all-url
title: Applying numeric coercion to all URL search params corrupts string IDs; coercion should be restricted to known numeric k
status: active
attributed_to: user
entities: [URL search params, numeric coercion, ID]
links: []
hash: 5a74b31b
confidence: 0.9
source: /home/user/github/LimbusPlanner/docs/tasks/007-planner-list/08-search-by-items/results.md
---

Applying numeric coercion to all URL search params corrupts string IDs; coercion should be restricted to known numeric keys only (e.g., `page`) to preserve ID type integrity.
