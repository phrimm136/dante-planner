---
name: checks
description: Run the repo's frontend, e2e and backend checks (typecheck, lint, format, vitest, playwright, gradle test/check/checkstyle/pitest, the PR Gate frontend steps, the skill CLI and git-hook test suites) through one local CLI that takes the test lock, writes a dated log and prints PASS/FAIL. Use for any test, typecheck, lint, build or format run, when the Bash hook refuses a hand-assembled check, or when a check's tools or node_modules look stale.
---

# checks

Every repeated local check runs as `checks <lane> <check> [args]` (ADR 509). The CLI takes
`<main checkout>/.claude/.test.lock`, writes the tool's whole output to
`.claude/reports/logs/checks-<lane>-<check>-<YYYYMMDD-HHMMSS>.log`, and prints `PASS <log>` or
`FAIL <log>` with the tool's own summary; the exit code is the tool's. Read the log, never re-run
to see output again. The `check-output-redirect.sh` hook refuses direct vitest, tsc, oxlint,
oxfmt, playwright, gradlew and the matching yarn scripts, and names the `checks` command instead.
CI does not call this CLI.

The CLI checks the checkout it lives in, whatever the shell's directory: from a worktree run
`<worktree>/.claude/skills/checks/bin/checks`; `--root <path>` points it elsewhere. Do not wrap it
in `flock`: it takes the lock itself.

| Command | Use |
|---------|-----|
| `doctor` | First, before any run: lock holder, static link, node_modules against each lockfile, oxlint/oxfmt/vitest/playwright versions, Docker, JDK; each problem names its fix |
| `list` | Every command with its purpose |
| `fe <check>` | `typecheck`, `lint`, `lint-validation`, `fmt`, `fmt-check`, `fp`, `rules`, `bailouts`, `build`, `budget`, `test [vitest args]` |
| `fe gate` | The PR Gate `test-frontend` steps, read from `pr-gate.yml`, under one lock, one line per step |
| `e2e <check>` | `typecheck`, `fmt`, `fmt-check`, `run [spec...] [--base-url URL] [--repeat N] [--jobs N] [--project NAME] [-- playwright args]` |
| `be <check>` | `check`, `test [--tests PATTERN] [--exclude-tags containerized]`, `checkstyle`, `pit` |
| `tools test` | Every `.claude/skills/*/tests/` directory (pytest) and `.githooks/tests/*.sh` script, through `.github/scripts/tooling-tests.sh`, the script PR Gate's `check-keep-list` job runs; one line per suite |
| `--print` | Show a run's directory, environment and command line without running it |

The CLI's own tests run with the other tooling suites: `checks tools test`.

Flags, log location and messages: `checks --help`.
