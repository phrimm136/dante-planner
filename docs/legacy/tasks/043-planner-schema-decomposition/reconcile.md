# Reconcile: Planner god-table decomposition

Fresh-context audit of `git diff task/043-planner-schema-decomposition-base..HEAD`
against `spec.md`, three lenses (row coverage, intuition, bugs), auditors without
authoring context. Full lens reports were generated per-audit; this file is the
canonical record.

## Coverage (the gate)

**22/22 rows match · 7/7 invariants match · 0 drift · 0 MISSING.**
(The spec front-matter says 23 rows; the `## Rows` section contains 22 ids.)

| row id | verdict | implementing code | test |
|--------|---------|-------------------|------|
| concurrent-upsert-yields-409 | match | PlannerCommandService.upsertPlanner (optimistic lock); PK-only planner_content (V049) | PlannerUpsertConflictIT |
| concurrent-vote-no-deadlock | match | PlannerEngagementService.castVote → stats upsert; vote FK → write-once core | MySQLIntegrationTest.concurrentVote_NoDeadlock… |
| detail-view-count-async | match | PublishedPlannerQueryService.getPublishedPlanner + PlannerViewRecorder.flush | PlannerStatsIsolationIT |
| counter-reads-from-stats | match | list/detail counters from planner_stats only | PlannerStatsIsolationIT |
| catalog-membership-on-transition | match | PlannerCatalogService.add/remove wired into publish/delete/takedown | PlannerCatalogLifecycleIT |
| list-recency-sort | match | findAllByOrderByFirstPublishedAtDesc over idx_catalog_recent | PlannerCatalogLifecycleIT (EXPLAIN: no filesort) |
| title-search-ngram | match | CatalogSpecifications.matchesQuery + match_against (ngram, boolean mode) | PlannerTitleSearchIT (4 cases) |
| q-matches-title-or-keyword | match | title FULLTEXT OR keyword EXISTS | PlannerTitleSearchIT |
| entity-facet-filter | match | containsEntity EXISTS on planner_entity_filter | PlannerKeywordFacetIT |
| keyword-facet-filter | match | hasKeyword EXISTS on planner_keyword_filter | PlannerKeywordFacetIT |
| keyword-rename-normalized-on-write | match | PlannerKeywords.fromClient at the domain boundary | PlannerKeywordFacetIT |
| keyword-read-passthrough-total | match | PlannerKeywords.fromStorage total; converter tolerant | PlannerKeywordFacetIT |
| published-title-edit-consistent | match | synchronous catalog scalar copy | PlannerPublishFlowIT |
| publish-single-request | match | publishWithContent (upsert + publish, one tx); bodyless toggle kept | PlannerPublishFlowIT |
| comment-count-maintained | match | stats increment on create/reply, floored decrement on delete transitions | PlannerStatsIsolationIT |
| recommended-flag-and-detail | match¹ | catalog.recommended derived on vote-crossing / hide / unhide | PlannerCatalogLifecycleIT |
| unpublished-changes-visible-to-owner | match | owner detail carries status + published (INV7: computed, never stored) | PlannerResponseContractIT |
| takedown-blocks-republish | match | aggregate root rejects publish while taken down | PlannerCatalogLifecycleIT |
| user-delete-sweeps-aggregate | match | app-side sweep incl. report tables, then core cascade | PlannerUserDeleteSweepIT (14 tables) |
| response-contract-stable | match | detail/owner-list DTOs field-identical; renames internal | PlannerResponseContractIT (field-set pins) |
| list-card-fields | match | card gains firstPublishedAt, drops contentVersion/lastModifiedAt | PlannerResponseContractIT |
| reconciler-detects-drift | match | PlannerDriftReconciler: 5 checks, structured records + metric, no repair | PlannerReconcilerIT (7 seeded drifts) |

Invariants INV1–INV7: all match via their stated verification (rows, schema drill
`PlannerSchemaDecompositionIT`, reconciler drill `PlannerReconcilerIT`).

¹ The row's secondary clause — "the detail computes recommended live" — has no
observable output: no detail DTO (base or current) carries a `recommended` field,
so there is nothing to compute or assert. The list-side derivation the row
primarily enforces is fully covered. Flagged for a spec amendment ruling.

## Intuition

The implementation is the design the spec ratified, not a lookalike: a real
root-coordinated write aggregate (satellites share the core PK via @MapsId; the
owner hot path dirty-checks only planner_content), public reads assembled as
catalog ⨝ stats ⨝ planner→users with the detail as the sanctioned aggregate read,
and the maintenance matrix implemented event-for-event — synchronous catalog
scalar copies, AFTER_COMMIT filter rebuilds skipped on unchanged composition,
clears on every visibility loss, recommended refresh on vote-crossing and
hide/unhide. Migrations follow the 4-step stop-the-world plan with parity
assertions ahead of the drops. No dead code from the pre-split design survives;
the old god-entity, index service, stats-read flag, and LIKE-based search are
gone rather than stranded.

Minor observations (no divergence): the "filters vs a rebuild dry-run" parity is
satisfied by construction (the backfill runs the runtime extractor) rather than
asserted; V052's counter parity sums views+upvotes together, so it is
swap-insensitive.

## Findings

Ranked; verdicts applied before this record was written.

1. **Keyword backfill skipped normalization** (was HIGH; V050/V051) — the
   filter backfill inserted stored keyword members verbatim while the runtime
   path normalizes renames and drops unknowns, so a legacy alias at rest would
   be unsearchable under its current id and would drift the reconciler forever.
   Mitigating fact: the V038/V044 rename migrations rewrote rows and removed
   aliases from the SET definition, so such rows cannot exist in real data.
   **Fixed** — V050 applies the rename mapping in its CSV-to-JSON conversion,
   so the keyword source V051 indexes is normalized at rest.
2. **CI migration gate never executes the Java migration** (MEDIUM;
   ci-backend.yml applies `.sql` only) — a Java migration was invisible to the
   migration smoke job. **Fixed by design change** (spec amendment A1): the
   filter backfill was converted to plain SQL (JSON_TABLE over the same paths
   the runtime extractor indexes), so the CI gate executes the entire chain;
   runtime parity is enforced by the reconciler's filter check.
3. **Cross-path AFTER_COMMIT ordering window** (MEDIUM) — an owner-edit rebuild
   event and a moderation clear event have no mutual ordering; a late rebuild
   can repopulate filters for a just-taken-down planner until the reconciler
   flags it. **Deferred** — the ratified async-maintenance tradeoff; the
   reconciler is the designed backstop.
4. **FK repoint crash window** (LOW; V052) — a crash between the DROP and ADD
   of a repointed FK skipped re-creation on rerun. **Fixed** — the procedure now
   guards the drop and the add independently.
5. **INSERT IGNORE masks source-row defects** (LOW; V050) — a constraint-violating
   legacy row would be skipped silently, caught only by the pre-drop count
   parity abort. **Skipped** — the parity assertion is the designed backstop;
   failure diagnosis cost accepted.

## Review lanes

Scoped architecture and security reviews over the same diff (fresh context).

Security: no findings — search input is parameter-bound end to end, the
content-carrying publish path cannot cross owners, facets read only the
visible-only catalog, migration procedures concatenate hardcoded identifiers
only.

Architecture: three findings, verdicted.
1. Visibility-transition choreography repeated per write seam (MEDIUM) —
   **fixed**: `PlannerCatalogService` gained the transition intents
   (`onBecameVisible` / `onBecameInvisible` / `onVisibleEditCommitted`)
   that pair the synchronous catalog op with the post-commit filter event, so
   callers express intent instead of wiring two services in lockstep.
2. Satellite transition mutators reachable around the root's guards (MEDIUM) —
   **fixed**: publication/moderation transitions are package-private (only the
   root can call them) and the owner-notification preference goes through a
   root delegator, making the takedown-blocks-republish invariant structural.
3. Dual query mechanisms on the catalog repository (LOW) — **skipped**: the
   derived finders name the fixed browse shapes the row tests pin; divergence
   between the two idioms is caught by those tests.

## Diagrams

<pre>
write path                          read path
──────────                         ──────────
owner save ──► planner_content     list ──► planner_catalog ⨝ planner_stats
        │        (PK-only row)              ⨝ planner→users
        ├─sync─► planner_catalog   detail ─► planner+content+publication
        └─after-commit─► filters             +moderation (+stats lookup)
voters/viewers/commenters ──► planner_stats (atomic upserts only)
</pre>
