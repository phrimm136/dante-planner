---
uid: 01KXK6EYJS20YFWSTSQ8X5FTZT
created: 2026-07-15T15:31:25Z
updated: 2026-07-19T07:25:54Z
scope: global
category: gotcha
origin: draft
title: happy-dom serializes inline style colors to raw hex notation (e.g., #aabbcc), while jsdom normalizes to rgb(a,b,c) form;
status: active
attributed_to: llm
entities: [happy-dom, jsdom]
hash: 4dfa3ce7
source: text
---

happy-dom serializes inline style colors to raw hex notation (e.g., #aabbcc), while jsdom normalizes to rgb(a,b,c) form; direct string comparison of el.style.color will fail when testing across both environments.
