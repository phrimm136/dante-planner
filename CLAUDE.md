# Dante's Planner

Game planning and management tool for Limbus Company. React + TypeScript + TanStack
Query frontend, Spring Boot backend, static JSON data validated at runtime.

learning: system-design

## Rules

- No drive-by refactors, improvements, or unrelated fixes. A pattern fix still covers every site
  sharing the pattern.
- No hardcoded values; use the constants files.
- Validate at boundaries: Zod (FE), Jakarta Validation (BE).
- Bugs route through the `diagnose` skill: red feedback loop first, root cause named
  before any fix; never bypass an error to make a symptom disappear.
- Prod reads (SQL, logs, metrics, the RDS tunnel, verification) and terraform plans run only
  through the `ops` skill's CLI, `<checkout or worktree>/.claude/skills/ops/bin/ops <command>`
  (`ops list`), which names the AWS profile (`--env`, default prod); the hook refuses direct
  calls to the scripts it wraps. See `docs/external-access.md`; never improvise SSH/kubectl paths.
- Checks (tests, typecheck, lint, format, build, e2e, gradle) run only through
  `<checkout or worktree>/.claude/skills/checks/bin/checks` (`checks list`). Other yarn commands
  take `--cwd <dir>`.
- A skill CLI explains itself through `<cli> help`; the hook refuses reading its source unless the
  unit changes that CLI and created `<checkout>/.claude/.cli-edit`.
- Where a route's or a file's code lives comes from the `map` skill's CLI,
  `<checkout or worktree>/.claude/skills/map/bin/map` (`map help`): `map show '<route>'` and
  `map deps <file>` fill a brief's Module placement and Dependents before any search.
- Terraform plans run as `ops tf plan <stack>`, which prints the `!` apply command for the user;
  other terraform commands run through `scripts/ops/terraform-run.sh …`; a bare `terraform` is
  hook-blocked.
- No exclamation marks in responses.

## Docs

- `docs/README.md` routes the record system: `adr/` (decisions), `runbooks/`
  (procedures), `debt.md` (found work, pruned on write).
- Reviewer-finding handling: `docs/review-calibration.md`.
- `static/` has its own `CLAUDE.md` — read it before touching data, images, or UI
  layout.

## Git

- Before a unit's first commit, rebase its branch onto `origin/main`
  (`git -C <worktree> fetch origin main`, then `git -C <worktree> rebase origin/main`).
- Docs and data written for a unit go in the same commit as its code.
