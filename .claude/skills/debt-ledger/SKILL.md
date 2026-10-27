---
name: debt-ledger
description: Ask every entry of docs/debt.md whether its debt still holds, and check that every entry carries a re-check line. Use when adding, editing or pruning a debt entry, when the pre-commit hook or the weekly Debt Check workflow reports GONE or BROKEN entries, or before a defrag or design session reads the ledger.
---

# debt-ledger

Every entry in `docs/debt.md` ends in one re-check line, `  re-check: \`<command>\``. The
command runs from the repository root with `sh -c` and a 5 s budget: exit 0 means the debt still
holds, exit 1 means the debt is gone, any other exit or a timeout means the command is broken
(ADR 201). It uses read-only tools only (rg, test, git ls-files, git log, jq, python3 -c, sh
builtins). "This must be absent" is written `.claude/skills/debt-ledger/bin/debt absent <path>
<rg arguments>`, never `! rg`, which reads a missing path as absent. A `python3 -c` re-check
starts with `sys.excepthook=lambda t,e,b: (print(f'{t.__name__}: {e}',file=sys.stderr),os._exit(2))`
so an exception exits 2 (broken), not 1 (gone).
Run `.claude/skills/debt-ledger/bin/debt` from inside the code checkout.

| Command  | Use |
|----------|-----|
| `doctor` | First, before any run: entry count, entries without a re-check line, duplicate titles, re-check lines that negate rg (`malformed`); exit 1 when any list is not empty |
| `check`  | Run every re-check line: `HOLDS`, `GONE` or `BROKEN` per entry; exit 1 with a count line when an entry is gone or a command is broken. `--only <substring>` runs the entries whose title contains it |
| `absent` | `absent <path> <rg arguments>`, inside a re-check line: exit 0 when the path exists and rg finds nothing, 1 when rg finds a match, 2 when the path is missing or rg fails |

A `GONE` entry is deleted. A `BROKEN` command is fixed in the entry. The pre-commit hook runs
both commands whenever `docs/debt.md` is staged, and the weekly Debt Check workflow runs `check`
on `main`.

Flags and messages: `debt --help`.
