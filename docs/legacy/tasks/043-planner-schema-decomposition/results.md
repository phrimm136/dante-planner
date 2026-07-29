# Results: Planner god-table decomposition

## Generated

### Phase series (base `task/043-planner-schema-decomposition-base` → HEAD, 22 commits)

| Seam | Commits |
|---|---|
| Persistence cutover (V049–V052, aggregate, projections, service rewires) | `d17ac7b0` test → `27f8073a` feat → `1ca88637` close |
| Deadlock elimination (R2) | `7cd201e8` |
| Stats isolation (R3) | `5f90b08f` test → `aec8c246` feat |
| Catalog lifecycle (R4) | `86e6598a` |
| Keyword VO + facets (R5, R6) | `0c16a6ee` test → `7a7db1df` feat |
| ngram title search (R4) | `d5ac5137` |
| One-request publish + post-commit maintenance (R7) | `4bb5eed7` test → `d3f7b3f3` feat |
| Wire-contract pins (R9) | `52544a72` |
| User hard-delete sweep (R1) | `d82c15be` test → `756bc062` feat |
| Drift reconciler (R8) | `ff5d87bb` test → `13f7fb91` feat |
| Post-phase: naming rule, audit fixes, SQL backfill (A1), review refactor, flake fix | `8087d266` `a9192a58` `a1a4006d` `371a0d98` `0687ea4a` |

### Rows proven

Per `reconcile.md`: **22/22 rows match, 7/7 invariants match, 0 drift, 0 MISSING.**
One caveat carried: `recommended-flag-and-detail`'s detail-side clause has no wire
observable (no DTO carries `recommended`) — flagged for a spec ruling.

### Findings ledger

| Finding | Verdict |
|---|---|
| Keyword backfill skipped rename normalization (bug lens, HIGH→mitigated) | fixed (`c718f627`, then subsumed by `2e9b4a1f`) |
| CI migration gate blind to Java migrations (MEDIUM) | fixed by design change — all-SQL chain (A1, `2e9b4a1f`) |
| Cross-path AFTER_COMMIT ordering window (MEDIUM) | deferred — ratified D8 tradeoff, reconciler is the backstop |
| V052 FK-repoint crash window (LOW) | fixed (`c718f627`) |
| V050 INSERT IGNORE diagnosability (LOW) | skipped — pre-drop parity abort is the backstop |
| Visibility choreography duplicated per write seam (architecture, MEDIUM) | fixed (`20fe65f6`) |
| Satellite mutators bypass root guards (architecture, MEDIUM) | fixed (`20fe65f6`) |
| Dual catalog query idioms (architecture, LOW) | skipped — row tests pin both shapes |
| Security lane | zero findings |

### Verify result

Branch-built container against isolated MySQL/Redis: full V001→V052 Flyway chain
applied to a virgin database on boot, then live HTTP: create → stale-sync 409 →
one-request publish of a never-synced draft → trimmed list card → identity /
renamed-keyword / ngram-substring search hits → vote → stats-served detail
counters → bodyless unpublish emptying the catalog. Full backend suite green
(1143 tests) at HEAD.

### Deferred carried forward

Spec originals unchanged: GA → Cloudflare write-routing; `device_id` column drop
(pending FE confirmation); `planner_document` blob split (dissolved);
title-FULLTEXT split-out; takedown-restore path; FTS deleted-doc reclamation;
keyset pagination. Added by audit: the AFTER_COMMIT rebuild/clear ordering
window; V050 skipped-row diagnosability.

## Narrative delta

1. **Changed — filter backfill mechanism (A1, ratified).** The spec mandated an
   app-side backfill running the runtime extractor ("NOT hand-rolled SQL") for
   code-path parity. Built first as a Flyway Java migration, it was invisible to
   the CI migration smoke gate, which executes `.sql` only — the parity guarantee
   traded away the only pre-merge rehearsal of the backfill against seeded legacy
   data. Converted to `JSON_TABLE` SQL over the same JSON paths; parity is now
   enforced by the drift reconciler's filter check instead of code-path identity.
   Root cause: the spec weighed extraction parity but never stated "the migration
   chain must be executable by the CI gate" as a requirement.
2. **Wrong-assumption — seed vs CI ordering.** The spec required the smoke-test
   seed be updated to the new schema "in the same change", assuming that was
   compatible with the CI job, which loads the seed *between* the merged and the
   new migrations — a new-schema seed would have broken this very change's
   migration rehearsal. Resolved by making the CI step load the seed as of the
   base branch, so the file tracks the new schema for future changes while this
   change rehearses against legacy-shaped data. No amendment filed: the spec
   sentence's intent (seed tracks schema) was preserved; the conflict was in an
   unstated CI mechanic.
3. **Added — visibility-transition module and sealed aggregate guards
   (no amendment).** Architecture review found the catalog+filter pairing
   hand-wired at every write seam and the satellite transition mutators publicly
   reachable around the root's invariant guards. Both fixed as structure-only
   refactors that deepen ratified decisions (D8's cost split, D14's
   root-enforced invariants) without changing any row's behavior — hence no
   amendment.
4. **Dropped (pending ruling) — detail-side live `recommended`.** The row's
   secondary clause describes a computation with no observable output in any
   DTO, past or present. Nothing was built for it and nothing could be asserted;
   flagged in `reconcile.md` rather than silently satisfied. Needs either a spec
   amendment deleting the clause or a decision to add the field.

takeaway: specs here ratify *what the system does* but under-specify *what the
delivery machinery must be able to execute* — both divergences (A1, the seed/CI
ordering) came from migration-mechanism claims that no row could test and the CI
gate could not rehearse; when a spec prescribes an implementation mechanism,
ratify its executability by the pipeline as an explicit requirement.
