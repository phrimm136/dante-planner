---
name: map
description: Find where a frontend route's code lives - its page, the files only it uses, the files it shares, the API endpoints it calls with their backend controllers, and its tests - or what one file imports, what imports it and which routes reach it. Use before writing a brief, before editing an unfamiliar page or hook, and to learn which routes a change to a file touches.
---

# map

`<checkout or worktree>/.claude/skills/map/bin/map` computes every answer from `router.tsx`, the
import graph under `frontend/src` and the Spring controllers (ADR 518). Nothing is stored, so
nothing goes stale. It reads the checkout it lives in; `--root <path>` names another.

| Command | Use |
|---------|-----|
| `list` | Every route with its page and how many files it reaches |
| `show '<route>'` | A route's page, own files, narrowly shared files, endpoints (frontend caller and backend handler) and tests |
| `for <file>` | The routes that reach a file |
| `deps <file>` | A file's imports, importers, tests, routes and endpoints |
| `doctor` | What the map could not read; run it when an answer looks wrong |

Arguments, path forms and limits: `map help`.
