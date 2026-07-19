---
uid: 01KXK6EYJ7RZEJRAM4EKFAEH9C
created: 2026-07-15T15:31:25Z
updated: 2026-07-19T07:25:54Z
scope: LimbusPlanner
category: decision
origin: draft
title: Frontend vitest configured with three projects: 'src' (happy-dom + threads, general UI tests), 'src-editor' (jsdom + thr
status: active
attributed_to: llm
entities: [vitest, happy-dom, jsdom, Tiptap, ProseMirror]
hash: 457374cf
source: text
---

Frontend vitest configured with three projects: 'src' (happy-dom + threads, general UI tests), 'src-editor' (jsdom + threads, Tiptap/ProseMirror editor tests), 'plugin' (forks pool, for tests calling process.chdir). Rejected isolate:false (mock bleed) and tmpfs MySQL (concurrent container load up to 13, 13GB RAM limit).
