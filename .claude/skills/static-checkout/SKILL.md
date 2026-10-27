---
name: static-checkout
description: Check and move the game-data checkout that <repo>/static points at, and the sha static.lock pins. Use when static/ is missing or stale in a checkout or worktree, before and after bumping static.lock, or when data on disk disagrees with what the code expects.
---

# static-checkout

`static` in every checkout and worktree is an untracked symlink to one data checkout,
`~/github/LimbusPlanner-static`; `static.lock` pins the sha the code expects (ADR 200).
Run `.claude/skills/static-checkout/bin/static` from inside the code checkout.

| Command  | Use |
|----------|-----|
| `doctor` | First, before any run: link target, checkout HEAD, lock sha; exit 1 on a mismatch |
| `check`  | What builds, tests and the pre-commit hook call: silent on a match, exit 1 on a mismatch, exit 2 with no lock or checkout; `STATIC_LOCK_CHECK=off` skips it in vite and gradle |
| `link`   | Create `<repo>/static` in a new checkout or worktree |
| `lock`   | Adopt the data checkout's HEAD into `static.lock` (a code commit) |
| `sync`   | Move the data checkout to the lock's sha |

Flags and messages: `static --help`.
