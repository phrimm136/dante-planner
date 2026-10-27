---
name: debt-ledger
description: Ask every entry of docs/debt.md whether its debt still holds, and check that every entry carries a re-check line. Use when adding, editing or pruning a debt entry, when the pre-commit hook or the weekly Debt Check workflow reports GONE or BROKEN entries, or before a defrag or design session reads the ledger.
---

# debt-ledger

Every entry in `docs/debt.md` ends in one re-check line, `  re-check: \`<command>\``. The
command runs from the repository root with `sh -c` and a 5 s budget: exit 0 means the debt still
holds, exit 1 means the debt is gone, any other exit or a timeout means the command is broken
(ADR 201). Every clause of the command (split on `&&`, `||`, `;` and `|` outside quotes; a
`$(…)` is a clause of its own) starts with an allowed tool once a leading `!`, `env -C <dir>` and
`NAME=value` assignments are set aside: `rg`, `test`, `[`, `git`, `jq`, `awk`, `true`, `false`
or `.claude/skills/debt-ledger/bin/debt`. "This must be absent" is written
`.claude/skills/debt-ledger/bin/debt absent <path> <rg arguments>`, never `! rg`, which reads a
missing path as absent. A Python re-check is written
`.claude/skills/debt-ledger/bin/debt python '<body>'`, never by calling an interpreter, which
exits 1 (gone) when the body fails to compile or raises.
Run `.claude/skills/debt-ledger/bin/debt` from inside the code checkout.

| Command  | Use |
|----------|-----|
| `doctor` | First, before any run: entry count, entries without a re-check line, duplicate titles, re-check lines that negate rg or start a clause with a tool off the allowed list (`malformed`); exit 1 when any list is not empty |
| `check`  | Run every re-check line: `HOLDS`, `GONE` or `BROKEN` per entry; exit 1 with a count line when an entry is gone or a command is broken. `--only <substring>` runs the entries whose title contains it |
| `absent` | `absent <path> <rg arguments>`, inside a re-check line: exit 0 when the path exists and rg finds nothing, 1 when rg finds a match, 2 when the path is missing or rg fails |
| `python` | `python '<body>'`, inside a re-check line: compiles and runs the body; `sys.exit(0)` or `sys.exit(1)` passes through, a compile error, any other exception or any other exit code exits 2 with `<Type>: <message>` |

A `GONE` entry is deleted. A `BROKEN` command is fixed in the entry. The pre-commit hook runs
both commands whenever `docs/debt.md` is staged, and the weekly Debt Check workflow runs `check`
on `main`.

Flags and messages: `debt --help`.
