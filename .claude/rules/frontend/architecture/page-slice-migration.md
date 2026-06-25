# Page-Slice Migration Playbook

How to move a route + its code into `src/pages/<slice>/`. Proven across the extraction,
game-noun, planner, and app-shell-pages migrations. The boundary model itself is in
`frontend/CLAUDE.md` (the "Page slice" quick-reference row).

## Decide what moves vs. stays — grep FIRST

Before moving any file, grep ALL of `src` for who imports it from OUTSIDE the page:

```bash
grep -rn "@/components/<name>" src | grep -v -e "components/<name>/" -e "routes/<Page>"
```

**Dual-consumer rule (decision procedure):** a module imported by 2+ domains STAYS in shared
space (`components/common`, `hooks/`, `lib/`, `types/`, `schemas/`) — it is NEVER moved into a
consuming slice. Only files used solely by the one page move into the slice.

Tracing what the page *imports* is not enough — you must trace who imports the page's *components*.
That reverse direction is where dual-consumers hide (e.g. `CommunityPlansErrorFallback` =
home+planner; `BanDialog` exporting `CommentDeleteDialog` = moderator+comment). Missing one
surfaces as a `tsc` break mid-migration.

## Execute the move directly — not via code-writer agents

A pure relocation has no new logic; agents Read→Write and risk content drift/reformatting.

1. `mkdir -p src/pages/<slice>/<segment>` (git mv needs the destination dir to exist).
2. `git mv` each file. Verify `git status` shows `R`/`RM` (rename), not `D`+`??`.
3. Rewrite imports (see below).
4. `git diff` each moved file shows ONLY import lines changed — any body churn means drift.

## Import rewrites

- **Intra-slice imports MUST be relative** (`./components/X`, `../hooks/Y`). The eslint
  `no-restricted-imports` rule bans `@/pages/*/**` even from a slice's own files. The bare
  `@/pages/<slice>` barrel is allowed but only exists if the slice has an `index.ts`.
- **Cross-slice / shared imports stay `@/...`** (unchanged): `@/pages/<other>` public API,
  `@/components/common`, `@/hooks`, `@/lib`.
- **`index.ts` only if externally consumed.** A page nothing imports from (router deep-imports it
  and is eslint-exempt) needs no public API. Do not add empty barrels.
- **Repoint `vi.mock` paths** in moved tests to the new module location
  (`@/components/home/X` → `@/pages/home/components/X`). A stale `vi.mock` path silently stops
  intercepting — the test then renders the real component and may pass for the wrong reason.
  (These are string args, not import statements, so the lint boundary rule does not flag them.)
- Update the lazy import in `src/lib/router.tsx` (the only file exempt from the boundary rule).

## Verify per phase

Migrate one slice at a time; after each: `tsc -b` (isolates a break to the slice that caused it),
then grep all of `src` for the old paths (must be empty), then run that slice's tests. Boundary
invariant check (if `yarn lint` is unavailable): grep for deep `@/pages/*/**` import statements
outside `router.tsx` — must be empty.
