# Dante's Planner

Game planning and management tool for Limbus Company. React + TypeScript + TanStack
Query frontend, Spring Boot backend, static JSON data validated at runtime.

learning: design

## Rules

- Change only what was asked — no drive-by refactors, improvements, or unrelated fixes.
- No hardcoded values; use the constants files.
- Validate at boundaries: Zod (FE), Jakarta Validation (BE).
- Bugs route through the `diagnose` skill: red feedback loop first, root cause named
  before any fix; never bypass an error to make a symptom disappear.
- Prod reads (SQL, logs, metrics, the RDS tunnel, verification) and terraform plans run only
  through the `ops` skill's CLI, `<checkout or worktree>/.claude/skills/ops/bin/ops <command>`
  (`ops list`), which names the AWS profile (`--env`, default prod); the hook refuses direct
  calls to the scripts it wraps. See `docs/external-access.md`; never improvise SSH/kubectl paths.
- Checks (tests, typecheck, lint, format, build, e2e, gradle) run only through the `checks`
  skill's CLI, `<checkout or worktree>/.claude/skills/checks/bin/checks <lane> <check>`, which
  checks the checkout it lives in; the hook refuses direct vitest/tsc/oxlint/oxfmt/playwright/gradlew. Other yarn
  commands target directories with flags (`yarn --cwd frontend install`), and `cd` is deny-listed.
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

- Commit workflow: the `commit-process` skill.
