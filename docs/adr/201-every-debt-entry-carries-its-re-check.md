# 201 every-debt-entry-carries-its-re-check
epic: none · pr: none

## Decisions
- @debt @format — Every entry in the debt ledger ends in one machine-readable re-check line,
  `  re-check: \`<command>\``, with one polarity: the command exits 0 while the debt still holds
  and 1 when it is gone; any other exit, or a run past 5 s, marks the command as broken. An entry
  starts at a line beginning `- ` and ends before the next `- `, `## ` or the end of the file;
  `## ` headings only group entries. The command runs from the repository root with `sh -c` and
  uses read-only tools only: rg, test, `[`, git, jq, awk, true, false and the ledger CLI's absent
  and python commands; a clause that starts with anything else is malformed. Gradle, yarn,
  docker and the network are forbidden, and an absence is checked with the ledger CLI's `absent`
  command (0 nothing matched, 1 matched, 2 path missing). The old prose form
  ("Re-check: … prints nothing") is replaced, never kept beside the line. Without the line, no
  machine can ask whether an entry still holds, so deletions happened only in rare hand sweeps
  and the ledger grew between them.
  REJECTED: a structured file (YAML) that replaces the markdown — the markdown and its history
  stay readable as prose, and a parser of twenty lines reads the trailer.
- @debt @cli — One CLI in the debt-ledger skill reads the ledger: `doctor` counts the entries,
  names the ones without a re-check line (an unbalanced backtick counts as no line) and the
  duplicate titles; `check` runs every re-check line and prints `HOLDS`, `GONE` or `BROKEN` with
  the entry's title, the first sentence of the entry. A timed-out command is killed with its
  whole process group, so a check never leaves a command running behind it. The CLI drops the
  inherited git environment (`GIT_DIR` and its siblings) before it finds the repository root,
  because the pre-commit hook runs it with `GIT_DIR` exported. Every error message names the
  next action.
- @debt @gate — Two gates run the CLI: the pre-commit hook on every commit that stages the
  ledger, and a weekly scheduled workflow on `main` that reports the entries whose debt is gone.
  The schedule reports prune candidates; it does not pull work from the ledger. The hook refuses
  a staged ledger whose `check` reports a `GONE` entry or a `BROKEN` command. Until every entry
  carries the line, the hook does not require zero entries without one: it compares the staged
  ledger's missing count with the count at `HEAD` and refuses only growth. Removal condition:
  every older entry carries the line and `debt doctor` exits 0 on `main`; then the hook requires
  zero missing entries.
  REJECTED: a CI lane on every pull request — the ledger is read when it is written and once a
  week, not per pull request.
  INVARIANT: the hook refuses a ledger write whose count of entries without a re-check line grew
  against `HEAD` — the pre-commit hook.
  NON-GOAL: mechanizing the "worth doing when" condition — it is a judgment for a design session,
  and the re-check answers only whether the debt exists.
- @debt @absent — "This must be absent" is written with the debt CLI's `absent <path> <rg
  arguments>` command: it exits 0 when the path exists and rg finds nothing, 1 when rg finds a
  match, and 2 when the path is missing or rg fails. Python re-checks run through the CLI's
  `python <body>` command, which maps any compile or runtime failure to 2. The negated
  form `! rg -q …` turned rg's exit 2 for a missing path into 0, so a renamed path read as a
  debt that still holds, forever; and a `python3 -c` body that raised exited 1, which reads as a
  debt that is gone. Both broke the one-polarity contract.
  REJECTED: a `test -e <path> &&` guard on every negated line — a convention each author can
  forget, which is the failure the negated form had.
  REJECTED: `check` detecting `! rg` in the command text — the same convention, enforced only
  after the fact.
  REJECTED: a `sys.excepthook` prefix on every `python3 -c` body — a convention each author can
  forget, and a syntax error exits 1 before the hook is set.
  INVARIANT: every clause of a re-check command (split on `&&`, `||`, `;`, `|` and a lone `&`,
  with each `$(…)` a clause of its own) starts with an allowed tool and never with `!`: rg,
  test, `[`, git with a read-only subcommand (ls-files, log, diff, show, rev-parse, cat-file,
  grep), jq, awk, true, false or the ledger CLI — `debt doctor`, which counts every other line
  as `malformed`, names each entry and exits 1, and the pre-commit hook, which refuses a staged
  ledger whose `malformed` count is not zero.
  NON-GOAL: recursing into an allowed tool's other arguments (awk's `system()`, git's
  `-c diff.external=…`) — the allowlist bounds the first word of each clause and git's
  subcommand only.
  NON-GOAL: sandboxing a re-check — `doctor` is a lint against mistakes, not a sandbox; the 5 s
  budget and review bound what the lint does not.

## Takeaway
- takeaway: a record that cannot be asked by a machine whether it is still true is only pruned
  by hand, and hand pruning does not happen; give each record its own question.
