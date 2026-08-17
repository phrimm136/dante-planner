---
uid: 01KXWHN4F7S1QBBS1GWR07RBHP
created: 2026-07-19T06:40:12Z
updated: 2026-07-19T07:26:41Z
scope: global
category: gotcha
origin: draft
title: Spring Test caches application context by the SET of @DynamicPropertySource methods. Inheriting a single @DynamicPropert
status: active
attributed_to: llm
entities: [Spring Test]
hash: 3435dff1
source: text
---

Spring Test caches application context by the SET of @DynamicPropertySource methods. Inheriting a single @DynamicPropertySource from a base class collapses all subclasses onto the same cached context, defeating per-subclass property isolation.
