# Dante's Planner

Game planning and management tool for Limbus Company. React + TypeScript + TanStack
Query frontend, Spring Boot backend, static JSON data validated at runtime.

## Rules

- Change only what was asked — no drive-by refactors, improvements, or unrelated fixes.
- No hardcoded values; use the constants files.
- Validate at boundaries: Zod (FE), Jakarta Validation (BE).
- Bugs route through the `diagnose` skill: red feedback loop first, root cause named
  before any fix; never bypass an error to make a symptom disappear.
- Prod access (RDS SQL, Loki logs) only via `scripts/ops/access/` — see
  `docs/external-access.md`; never improvise SSH/kubectl paths.
- Target directories with flags: `yarn --cwd frontend …`,
  `/home/user/github/LimbusPlanner/backend/gradlew -p backend …`. A bare
  `vitest`/`tsc`/`gradlew` at the repo root is hook-blocked, and `cd` is deny-listed.
- No exclamation marks in responses.

## Docs

- `docs/README.md` routes the record system: `adr/` (decisions), `rfcs/` (design docs),
  `runbooks/` (procedures), `debt.md` (found work, append-only).
- Reviewer-finding handling: `docs/review-calibration.md`.
- `static/` has its own `CLAUDE.md` — read it before touching data, images, or UI
  layout.

## Git

- Commit workflow: the `commit-process` skill.
