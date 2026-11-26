# 900 every-kind
epic: none · pr: none

## Decisions
- @fixture @assert — A passing assert. Re-check [assert]: `test -d docs`.
- @fixture @assert — A failing assert. Re-check [assert]: `false`, which this fixture expects to fail.
- @fixture @assert — A broken assert. Re-check [assert]: `test 1 -gt x`.
- @fixture @absent — A passing absent. Re-check [absent]: `rg -q fixture-token-nothing docs`
  finds nothing.
- @fixture @absent — A failing absent. Re-check [absent]: `rg -q fixture-token-match docs`.
  REJECTED: a second command in a rejected clause — `false`.
- @fixture @needs — Needs the GitHub CLI. Re-check [gh]: `gh run list -w "PR Gate"`.
- @fixture @needs — Needs production. Re-check [prod-read]: `ops sql "SELECT 1"`.
- @fixture @needs — Needs a person. Re-check [manual]: `yarn --cwd frontend run typecheck` is refused.
- @fixture @prose — Reads a page. Re-check [prose]: the vendor's reference still says so.
- @fixture @assert — An assert with prose. Re-check [assert]: `test -d docs` succeeds, so the docs exist. Later text `may` quote.
- @fixture @quote — The old form ("Re-check: … prints nothing") is quoted, not a re-check line.

## Superseded
- @fixture @old → 901 — A superseded decision. Re-check: `false`.

## Takeaway
- takeaway: every kind has a fixture.
