# Phase 05: deploy-marker annotations — PASS

Scope: STATIC certification only. This is a live-only phase; the sole runtime proof
(a marker appears in Grafana) fires on the next production rollout and is consent-gated
to the user. No live call attempted. Live-appearance and live-token items are classified
DEFERRED-LIVE, not UNMET.

### Suite
No local suite exists for a workflow edit. Static gates run per the Brief:

```
$ python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy-fleet.yml')); print('yaml OK')"
yaml OK

$ git diff --stat .github/workflows/deploy-fleet.yml
 .github/workflows/deploy-fleet.yml | 19 +++++++++++++++++++
 1 file changed, 19 insertions(+)

$ grep -rni "grafana" .github/ deploy/ | grep -iE "bearer|\.grafana\.net|token|api.?key"
.github/workflows/deploy-fleet.yml:296:          GRAFANA_TOKEN: ${{ secrets.GRAFANA_ANNOTATIONS_TOKEN }}
.github/workflows/deploy-fleet.yml:307:              -H "Authorization: Bearer $GRAFANA_TOKEN" \
```
No literal token, no `*.grafana.net` instance URL — only a `${{ secrets.* }}` reference
and an env-indirected `Bearer $GRAFANA_TOKEN`.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| POST to Grafana annotations API after successful rollout, tagged region + SHA; token from GH Actions secret | plan/phase-05.md (external contract) | MET | `.github/workflows/deploy-fleet.yml:294-312` — step `Annotate Grafana with deploy marker` in `settle-down` job (job at :236), placed after "Wait for rollout" and before "Drain surge nodes"; `curl -X POST "$GRAFANA_URL/api/annotations"` (:306), body tags `deploy`/`cluster:$cluster`/`sha:$GIT_SHA` over loop `oregon seoul` (:300-303), epoch-ms `time` via `date +%s%3N` (:301) | Static: pyyaml well-formed; diff review confirms shape. No unit test (workflow step); live assertion is DEFERRED-LIVE |
| deploy-fleet.yml POSTs to Grafana annotations API, tagged region + SHA, token from secret | mechanics.md §4 (Deploy markers row) | MET | Same step; `GRAFANA_TOKEN: ${{ secrets.GRAFANA_ANNOTATIONS_TOKEN }}` (:296), `GRAFANA_URL: ${{ vars.GRAFANA_URL }}` (:297) | Same as above |
| Workflow references the Grafana token secret by name (provisioning is user-owned) | mechanics.md §5 | MET (reference half) | `${{ secrets.GRAFANA_ANNOTATIONS_TOKEN }}` referenced at :296; base URL non-secret `${{ vars.GRAFANA_URL }}` at :297 | Secret *existence* is a live prereq — see DEFERRED-LIVE below |
| Deploy marker annotation appears in Grafana on next production rollout | Done-When item 7 | DEFERRED-LIVE | Static half (POST shape + INV11) is MET above | User step: provision secret `GRAFANA_ANNOTATIONS_TOKEN` + var `GRAFANA_URL`, then run the next production rollout of `deploy-fleet.yml`; confirm a `deploy` / `cluster:*` / `sha:*` annotation lands in Grafana |
| No secret ever appears in the repo (repo-wide grep) | INV11 (local half) | MET | Grep over `.github/` + `deploy/` returns only the `${{ secrets.* }}` ref and env-indirected `Bearer $GRAFANA_TOKEN`; no literal token, no hardcoded instance URL | grep evidence in Suite block above |
| POST is a `settle-down` step after rollout status succeeds; `run:` uses only `github.sha`-derived / loop-literal values, never untrusted PR input | Plan implementation methods; `.github/workflows/CLAUDE.md` | MET | Step in `settle-down` (:236) after the rollout-wait step; `GIT_SHA: ${{ github.sha }}` at job env :242 (trusted, not PR input); `run:` block (:298-312) contains no `${{ }}` interpolation — only shell vars `$GIT_SHA`, `$short_sha`, `$cluster`, `$now_ms`, `$GRAFANA_URL`, `$GRAFANA_TOKEN`, `$body`; loop literals `oregon seoul` | Static diff review; env-indirection confirmed |

### Gaps
- DEFERRED-LIVE Done-When item 7 (live marker appearance): cannot be certified statically —
  requires the next consent-gated production rollout. User step: provision secret
  `GRAFANA_ANNOTATIONS_TOKEN` and var `GRAFANA_URL`, run the production `deploy-fleet.yml`
  rollout, and confirm a `deploy`/`cluster:<oregon|seoul>`/`sha:<full sha>` annotation
  appears in Grafana.
- DEFERRED-LIVE mechanics.md §5 (secret existence): the workflow references
  `secrets.GRAFANA_ANNOTATIONS_TOKEN` by name (MET); actual provisioning of the token value
  is user-owned and verified only when the rollout POST succeeds (non-fatal `|| echo
  "::warning::"` means an unprovisioned token degrades gracefully, not a build failure).

All statically-certifiable items (1, 2, 5, 6 and the reference half of 3) are MET; the only
open gaps are DEFERRED-LIVE and consent-gated. Verdict: PASS.
