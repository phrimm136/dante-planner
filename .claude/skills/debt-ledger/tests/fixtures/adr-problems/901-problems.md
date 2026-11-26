# 901 problems
epic: none · pr: none

## Decisions
- @fixture @unmarked — No kind. Re-check: `true`.
- @fixture @unknown — Unknown kind. Re-check [asert]: `true`.
- @fixture @tool — A tool outside the allowlist. Re-check [assert]: `ls docs`.
- @fixture @absent — An absent line that is not rg. Re-check [absent]: `test -e docs`.
- @fixture @nocommand — An assert with no command. Re-check [assert]: the guide still says so.
  REJECTED: a command in a rejected clause — `true`.
- @fixture @second — A second command. Re-check [assert]: `true` and `false` both hold.
- @fixture @lead — Claim before the command. Re-check [absent]: the guide says so, and `rg -q x docs` finds nothing.
- @fixture @fine — A passing assert. Re-check [assert]: `true`.

## Takeaway
- takeaway: every problem has a fixture.
