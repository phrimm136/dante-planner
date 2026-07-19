---
uid: 01KXK6EYHHPRAVJKKXJTCHHK8R
created: 2026-07-15T15:31:25Z
updated: 2026-07-19T07:25:54Z
scope: LimbusPlanner
category: gotcha
origin: draft
title: Running vitest with isolate:false causes mock-registry bleed when 75+ test files use top-level vi.mock with partial fact
status: active
attributed_to: llm
entities: [vitest]
hash: d1fc3205
source: text
---

Running vitest with isolate:false causes mock-registry bleed when 75+ test files use top-level vi.mock with partial factories; produces ~127 failures across 20+ files ('No X export is defined on the mock', singleton drift). Per-file isolation must be maintained for any test-speedup optimizations.
