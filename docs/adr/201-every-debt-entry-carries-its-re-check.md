# 201 every-debt-entry-carries-its-re-check
epic: none · pr: none

## Decisions
- @debt @format — Every entry in the debt ledger ends in one machine-readable re-check line,
  `  re-check: \`<command>\``, with one polarity: the command exits 0 while the debt still holds
  and 1 when it is gone; any other exit, or a run past 5 s, marks the command as broken. An entry
  starts at a line beginning `- ` and ends before the next `- `, `## ` or the end of the file;
  `## ` headings only group entries. The command runs from the repository root with `sh -c` and
  uses read-only tools only (rg, test, git ls-files, git log, jq, python3 -c, shell builtins);
  gradle, yarn, docker and the network are forbidden, and "this must be absent" is written
  `! rg -q …`. The old prose form ("Re-check: … prints nothing") is replaced, never kept beside
  the line. Without the line, no machine can ask whether an entry still holds, so deletions
  happened only in rare hand sweeps and the ledger grew between them.
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

## Takeaway
- takeaway: a record that cannot be asked by a machine whether it is still true is only pruned
  by hand, and hand pruning does not happen; give each record its own question.
