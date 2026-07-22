---
base: 22968c9bd427a2277e4f32bd83ddaa99d48f50a1   # mirror of task/043-planner-schema-decomposition-base tag
requirements: [R1, R2, R3, R4, R5, R6, R7, R8, R9]
---

# Planner god-table decomposition

Split the `planners` god-table into a rich write aggregate (`planner` core + `planner_content`
+ `planner_publication` + `planner_moderation` + `planner_stats`) plus deliberately-dumb read
projections (`planner_catalog`, `planner_entity_filter`, `planner_keyword_filter`). Structurally
eliminates the concurrent-upsert deadlock, isolates counters, and rebuilds list/search on visible-only
projections. Stop-the-world (server-down) migration preserving existing data.

## Requirements
- R1: Decompose `planners` into the aggregate + projection tables (schema, entities, repositories).
- R2: Eliminate the concurrent-upsert InnoDB lock-upgrade deadlock (503 → deterministic 409).
- R3: Isolate counters in `planner_stats`; drop the legacy `planners` counter columns + dual-write.
- R4: `planner_catalog` visible-only projection; recency-only sort; ngram-FULLTEXT title search.
- R5: Faceted search via `planner_entity_filter` (entities) + `planner_keyword_filter` (keywords).
- R6: Keywords clean-at-rest (validate-on-write + eager rename migration, pass-through read).
- R7: Async projection maintenance + consolidated publish (cross-region write latency).
- R8: Scheduled reconciler for stats / comment_count / catalog / filter / recommended drift.
- R9: API response DTOs stable except a deliberate list-DTO trim; `sort` param removed; `q` semantics change.

## Target schema (DDL)

Invariants: `planner` (core) is write-once (FKs to it can't deadlock); `planner_content` (owner-mutated)
has NO secondary index and NO inbound FK; no FKs among aggregate/projection tables (app-side sweep on
delete); FKs point only outward to `users` or at write-once `planner`.

```sql
-- CORE — write-once identity
CREATE TABLE planner (
    id            BINARY(16)  NOT NULL,
    user_id       BIGINT      NOT NULL,
    planner_type  ENUM('MIRROR_DUNGEON','REFRACTED_RAILWAY') NOT NULL,  -- immutable, stable set
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_planner_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    -- CASCADE is the backstop for core; user hard-delete app-sweeps satellites/projections/children first
);

-- CONTENT — owner is the ONLY writer; single optimistic-lock boundary; no index, no inbound FK
CREATE TABLE planner_content (
    planner_id             BINARY(16)   NOT NULL,
    title                  VARCHAR(255) NOT NULL DEFAULT 'Untitled',
    status                 VARCHAR(16)  NOT NULL,             -- {draft, saved}
    category               VARCHAR(50)  NOT NULL,             -- VARCHAR, never re-enum (INV V012)
    selected_keywords      JSON         NULL,                 -- display-only array; filter via planner_keyword_filter
    content                JSON         NOT NULL,
    content_schema_version INT          NOT NULL DEFAULT 2,   -- was schema_version
    game_content_version   INT          NOT NULL,             -- was content_version (6=MD6, 5=RR5)
    sync_version           BIGINT       NOT NULL DEFAULT 1,   -- client sync token
    row_lock_version       BIGINT       NOT NULL DEFAULT 0,   -- @Version (was `version`)
    device_id              BINARY(16)   NULL,                 -- UUID; drop pending FE
    last_modified_at       DATETIME(6)  NOT NULL,             -- post-split, only bumps on owner content writes
    deleted_at             DATETIME(6)  NULL,                 -- here for cross-device sync w/o join
    PRIMARY KEY (planner_id)
);                                                            -- saved_at dropped (== last_modified_at post-split)

-- PUBLICATION — owner lifecycle + prefs
CREATE TABLE planner_publication (
    planner_id                  BINARY(16)  NOT NULL,
    published                   BOOLEAN     NOT NULL DEFAULT FALSE,
    first_published_at          DATETIME(6) NULL,             -- release time = catalog sort key
    owner_notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (planner_id)
);

-- MODERATION — moderator/admin only
CREATE TABLE planner_moderation (
    planner_id              BINARY(16)  NOT NULL,
    taken_down_at           DATETIME(6) NULL,
    hidden_from_recommended BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by_moderator_id  BIGINT      NULL,
    hidden_reason           TEXT        NULL,
    hidden_at               DATETIME(6) NULL,
    PRIMARY KEY (planner_id),
    CONSTRAINT fk_pmod_moderator FOREIGN KEY (hidden_by_moderator_id)
        REFERENCES users(id) ON DELETE SET NULL               -- NULL+hidden_at = moderator deleted
);

-- STATS — every viewer/voter/commenter writes ONLY here (atomic increments)
CREATE TABLE planner_stats (
    planner_id              BINARY(16)  NOT NULL,
    view_count              INT         NOT NULL DEFAULT 0,
    upvotes                 INT         NOT NULL DEFAULT 0,
    comment_count           INT         NOT NULL DEFAULT 0,   -- inc + DEC + reconciler
    recommended_notified_at DATETIME(6) NULL,                 -- moved off planners
    PRIMARY KEY (planner_id)
);

-- CATALOG — visible-only projection (row present ⇔ published ∧ ¬deleted ∧ ¬taken_down); recency-only
CREATE TABLE planner_catalog (
    planner_id         BINARY(16)   NOT NULL,
    planner_type       ENUM('MIRROR_DUNGEON','REFRACTED_RAILWAY') NOT NULL,  -- list is type-scoped; future type filter
    category           VARCHAR(50)  NOT NULL,                 -- maintained copy
    title              VARCHAR(255) NOT NULL,                 -- maintained copy
    selected_keywords  JSON         NULL,                     -- display-only copy
    first_published_at DATETIME(6)  NOT NULL,                 -- "recently released" sort key
    recommended        BOOLEAN      NOT NULL DEFAULT FALSE,   -- derived: upvotes≥thr ∧ ¬hidden (list only)
    PRIMARY KEY (planner_id),
    INDEX idx_catalog_recent      (first_published_at DESC, planner_type, category),           -- browse: all-type or type/category-filtered; type/category are in-index residuals (low card); DESC = scan direction
    INDEX idx_catalog_recommended (recommended, first_published_at DESC, planner_type, category), -- recommended list (selective seek), optional type+category residual
    FULLTEXT INDEX ftx_title      (title) WITH PARSER ngram   -- ngram (token_size 2): CJK/substring ≥2 chars; sole access path — type/category residual on the matched set, recency filesort over it
);

-- SEARCH: content entities — all-integer inverted index
CREATE TABLE planner_entity_filter (
    entity_type ENUM('IDENTITY','EGO','EGO_GIFT','THEME_PACK') NOT NULL,
    entity_id   INT UNSIGNED NOT NULL,                        -- ids are integers (incl theme-pack 1001-3001)
    planner_id  BINARY(16)   NOT NULL,
    PRIMARY KEY (entity_type, entity_id, planner_id),         -- entity → planners
    INDEX idx_pef_planner (planner_id),                       -- planner → entities (rebuild/FK)
    CONSTRAINT fk_pef_planner FOREIGN KEY (planner_id)
        REFERENCES planner(id) ON DELETE CASCADE               -- FK to write-once core = deadlock-free
);

-- SEARCH: keywords — string slugs, separate inverted index
CREATE TABLE planner_keyword_filter (
    keyword    VARCHAR(64) NOT NULL,                          -- 'AStrokeOfDeath'
    planner_id BINARY(16)  NOT NULL,
    PRIMARY KEY (keyword, planner_id),
    INDEX idx_pkf_planner (planner_id),
    CONSTRAINT fk_pkf_planner FOREIGN KEY (planner_id)
        REFERENCES planner(id) ON DELETE CASCADE
);
```

Child aggregates unchanged in shape (`planner_votes`, `planner_views`, `planner_comments`,
`planner_bookmarks`, `planner_subscriptions`, `reports`) — only their FK target moves from
`planners(id)` to `planner(id)` (write-once core), or is dropped for the app-side sweep. Every
derived table (`planner_catalog`, both filters, and the `planner_stats` extension) ships a backfill.

## Rows
- id: concurrent-upsert-yields-409
  drives: PUT /api/planner/md/{id} (two concurrent writers, same syncVersion)
  given: a planner and two owner upserts that both pass the syncVersion check
  when: they race to flush
  then: one commits; the loser gets 409 CONCURRENT_WRITE (never 503)
  requirement: R2
- id: concurrent-vote-no-deadlock
  drives: PlannerEngagementService.castVote (two concurrent voters, same planner)
  given: two distinct users voting the same planner simultaneously
  when: both cast
  then: no 503; both counted in planner_stats.upvotes (or dup-key no-op if same user)
  requirement: R2
- id: detail-view-count-async
  drives: GET /api/planner/{id}
  given: a published planner viewed by a user
  when: the detail is read
  then: read is lock-free and returns the pre-request view_count; the increment lands via the scheduled flush into planner_stats only
  requirement: R3
- id: counter-reads-from-stats
  drives: list / detail / recommend reads + the recommend threshold
  given: a planner with view/upvote/comment counts
  when: any count is read or the upvote threshold applied
  then: the value comes from planner_stats (no counter columns remain on the aggregate)
  requirement: R3
- id: catalog-membership-on-transition
  drives: PlannerCatalogService via publish / unpublish / delete / takedown
  given: a planner changing visibility
  when: publish / unpublish / delete / takedown occurs
  then: a planner_catalog row exists after publish; it is absent after unpublish/delete/takedown (membership == published ∧ ¬deleted ∧ ¬taken_down)
  requirement: R4
- id: list-recency-sort
  drives: GET /api/planner/md/published
  given: several visible planners of one type
  when: the list is fetched (no sort param)
  then: ordered by first_published_at desc, served filesort-free by idx_catalog_recent
  requirement: R4
- id: title-search-ngram
  drives: GET /api/planner/md/published?q=...
  given: a planner titled "Bleed Team" and one whose title is Korean
  when: q="eed" (or a 2+char CJK substring, or "team bleed")
  then: ngram FULLTEXT matches substrings ≥2 chars incl. CJK, order-independent; q shorter than 2 chars matches nothing; boolean operators in q are escaped
  requirement: R4
- id: q-matches-title-or-keyword
  drives: GET /api/planner/md/published?q=...
  given: a planner with keyword "Sinking" but no "sinking" in its title
  when: q="Sinking"
  then: it is returned — q ORs the title FULLTEXT against an EXISTS on planner_keyword_filter (keyword-match preserved)
  requirement: R4
- id: entity-facet-filter
  drives: GET /api/planner/md/published?identity=X (etc.)
  given: planners containing various content entities
  when: filtered by identity/ego/gift/theme-pack
  then: only planners whose planner_entity_filter contains that entity are returned
  requirement: R5
- id: keyword-facet-filter
  drives: GET /api/planner/md/published?keyword=X
  given: planners with various keywords
  when: filtered by keyword
  then: only planners whose planner_keyword_filter contains it are returned
  requirement: R5
- id: keyword-rename-normalized-on-write
  drives: keyword persistence (write path)
  given: a client sends a legacy keyword name (e.g. "AccelBullet")
  when: the plan is saved
  then: it is normalized to "9828" before storage; unknown keywords are dropped (logged); sync does not fail
  requirement: R6
- id: keyword-read-passthrough-total
  drives: keyword read path
  given: a persisted JSON keyword array (possibly written by older code)
  when: it is read
  then: reading never throws; keywords come back current (renames migrated at rest); array parsed to Set
  requirement: R6
- id: published-title-edit-consistent
  drives: PUT /api/planner/md/{id} on a published planner (title change)
  given: the owner edits a published plan's title
  when: the save commits
  then: the public list and the detail both reflect the new title immediately (catalog scalar copy is synchronous)
  requirement: R7
- id: publish-single-request
  drives: publish endpoint
  given: an unsynced draft the owner publishes
  when: the publish request is sent
  then: one request carries the content and upserts + sets published atomically (no separate sync request)
  requirement: R7
- id: comment-count-maintained
  drives: CommentService.createComment / deleteComment / moderator delete
  given: a planner with comments
  when: a comment is created or deleted (non-deleted→deleted transition)
  then: planner_stats.comment_count increments / decrements (never below 0); the reconciler detects seeded drift
  requirement: R3, R8
- id: recommended-flag-and-detail
  drives: recommended list + GET /api/planner/{id}
  given: a planner above the upvote threshold and not hidden
  when: the recommended list is read / the detail is read
  then: the list uses catalog.recommended (single-table scan); the detail computes recommended live from planner_stats.upvotes ≥ threshold ∧ ¬moderation.hidden_from_recommended
  requirement: R4
- id: unpublished-changes-visible-to-owner
  drives: GET /api/planner/{id} (owner) — FE "unpublished changes" indicator
  given: a published planner edited after publish (status dirty)
  when: the owner reads the detail
  then: the response carries status + published so the FE derives "modified-but-not-published"
  requirement: R9
- id: takedown-blocks-republish
  drives: publish path on a taken-down planner
  given: a moderator-taken-down planner
  when: the owner attempts to publish
  then: rejected (aggregate root enforces the invariant; no catalog row created)
  requirement: R4
- id: user-delete-sweeps-aggregate
  drives: UserAccountLifecycleService hard delete
  given: a user with planners (published + draft)
  when: the account is hard-deleted
  then: all of the user's planner rows are removed across every aggregate + projection + child table (batch DELETE by planner id), then the user; no orphaned catalog/stats/filter rows remain
  requirement: R1
- id: response-contract-stable
  drives: PlannerResponse / PublishedPlannerDetailResponse / PlannerSummaryResponse (detail + owner-list)
  given: any detail or owner-list read
  when: serialized to the client
  then: detail/owner-list fields are identical to pre-decomposition (incl. deviceId, savedAt→last_modified_at, schemaVersion, contentVersion)
  requirement: R9
- id: list-card-fields
  drives: PublicPlannerResponse (public list card)
  given: the public browse/search list
  when: a card is serialized
  then: it carries first_published_at (release date) and drops contentVersion + lastModifiedAt (deliberate list-DTO trim; those stay on the detail DTO)
  requirement: R9
- id: reconciler-detects-drift
  drives: scheduled reconciler
  given: seeded drift in planner_stats / comment_count / catalog membership / a filter table / catalog.recommended
  when: the reconciler runs
  then: it emits a structured drift record (metric + log event) per drifted planner; it does NOT silently auto-repair
  requirement: R8

## Decisions
- D1: @planner-aggregate @foreign-keys @app-sweep — `planner_id` is the shared PK across all aggregate + projection tables; NO FKs among them; FKs point only outward to `users` or at the write-once `planner` core; deletion (planner or user) is an app-side batch sweep by planner id. rejected: FK cascade among the aggregate tables — an FK-child's shared lock is exactly what upgrades to the upsert deadlock (D2), and cascade couples deletion to the DB engine. enforcement: schema + rows `user-delete-sweeps-aggregate`, `catalog-membership-on-transition`.
- D2: @planner_content @deadlock @optimistic-lock — the owner hot-path writes ONLY `planner_content`, and NO table FK-references it. rejected: keeping owner-mutated columns on a row that FK-children reference (today's `planners`) — a same-tx FK-child insert takes a shared lock that upgrades S→X and deadlocks two racers (spike SP1: 80/80); also rejected @DynamicUpdate / drop-secondary-index — SP1 left them at 80/80, only the PK-only content table fixed it (0/80). enforcement: row `concurrent-upsert-yields-409`.
- D3: @planner_stats @counters — all counters live only in `planner_stats`. rejected: the perf-branch dual-write + `updatable=false` guard + `stats.reads-enabled` flag — a stop-the-world migration lets stats be authoritative outright, with no transitional dual-write state to carry. (evidence: spike SP1 + stop-the-world migration)
- D4: @planner_catalog @sort @fulltext — visible-only projection (membership == published ∧ ¬deleted ∧ ¬taken_down); recency-only sort by `first_published_at`; the browse index leads with `first_published_at`, `planner_type`/`category` ride behind as low-card ICP residuals (see D16); FULLTEXT `(title)` uses the ngram parser. rejected: a mirror projection retaining published/deleted/taken_down columns (the V046 5-tuple) — modelling visibility as row-presence collapses the index to a 2-column shape. enforcement: rows `catalog-membership-on-transition`, `list-recency-sort`, `title-search-ngram`.
- D5: @planner_entity_filter @planner_keyword_filter @inverted-index — content-entity facets → `planner_entity_filter` (entity_id INT UNSIGNED); keyword facets → `planner_keyword_filter` (keyword VARCHAR); both inverted with an `idx_planner` reverse index; FK → `planner(id)` CASCADE (write-once parent = deadlock-free). rejected: one polymorphic filter table (entity_id VARCHAR holding both integer entity ids and string keyword slugs) — VARCHAR-coercing the large integer index to fit the small keyword set taxes the common case; rejected a set-column keyword filter — non-indexable scan. (evidence: entity ids all integer incl theme-pack 1001-3001; keywords string slugs)
- D6: @title-search @fulltext @ngram — MySQL FULLTEXT with the ngram parser (`ngram_token_size` = 2, the default); the backend builds the boolean query from `q`, escapes FULLTEXT operators, and ORs it against an EXISTS on `planner_keyword_filter` (preserving today's title-OR-keyword search). rejected: the default word parser — cannot tokenize CJK, and the user base is Korean; rejected a suffix-table for exact substring — over-engineering at this scale; rejected `LIKE %x%` / `FIND_IN_SET` — non-sargable full scans. (evidence: PlannerRepository search ORs `selectedKeywords LIKE`)
- D7: @selected_keywords @keyword-converter — `selected_keywords` becomes JSON (display-only array, consistent with `content`); filtering moves to `planner_keyword_filter`; the converter reformats CSV→JSON array (keeping rename/drop-unknown normalization) and rebuilds the read set as `new HashSet<>(…)`; clean-at-rest (validate on write, migrate renames eagerly, pass-through read). rejected: MySQL SET — ALTER-per-keyword + non-indexable; rejected TEXT — works with the unchanged converter but the user chose JSON for consistency with `content`; rejected VARCHAR — forces a length recompute on each keyword addition. (evidence: SET non-indexable + 8 prior ALTER migrations)
- D8: @projection-maintenance @async @read-your-writes — split by cost: the cheap `planner_catalog` scalar copy is written SYNCHRONOUSLY in the write txn (read-your-writes for title-in-list); the expensive filter rebuild is deferred to an AFTER_COMMIT listener (REQUIRES_NEW), batched via `rewriteBatchedStatements`, and skipped when content composition is unchanged. rejected: fully-synchronous maintenance — a 6-statement cross-region write ≈ 2s; rejected fully-async including the catalog scalar — breaks title-in-list read-your-writes. enforcement: row `published-title-edit-consistent`.
- D9: @publish @api — publish is a single content-carrying request (upload-then-toggle atomically); unpublish stays a lightweight toggle. rejected: two requests (sync draft, then toggle published) — two sequential cross-region round-trips for one user action. enforcement: row `publish-single-request`.
- D10: @recommended @planner_catalog @planner_stats — `recommended` is a derived flag on `planner_catalog` for the LIST; the DETAIL computes it live from `planner_stats` + `planner_moderation`; `moderation.hidden_*` source + audit kept. rejected: the flag on `planner_stats` — a hot-write counter table must not carry a queried index; rejected on `planner_publication` — the vote path must not write publication. enforcement: row `recommended-flag-and-detail`.
- D11: @reconciler @drift — a scheduled reconciler validates stats vs child COUNT(*), comment_count, catalog membership, BOTH filter tables (vs `PlannerFilterService.rebuildFilters` output), and `catalog.recommended` (vs `upvotes ≥ thr ∧ ¬hidden`); it emits structured drift records and does NOT auto-repair. rejected: silent auto-repair — hides the bug that caused the drift; rejected a test-only invariant — misses prod drift on the AFTER_COMMIT filter rebuild (lost on a crash between commit and listener). (default)
- D12: @planner_moderation @foreign-key — `hidden_by_moderator_id` FK → `users(id)` ON DELETE SET NULL. rejected: sentinel-0 reassignment — a moderation-populated row (hidden_at set) with a NULL moderator unambiguously means "moderator deleted", so NULL loses no information here and avoids an app-side reassignment. (default)
- D13: @planner-keywords @value-object @converter — keyword validation + rename normalization live in a `PlannerKeywords` value object (`fromClient` may reject; `fromStorage` is total, never throws); the JPA converter is a thin format adapter that delegates to the VO. rejected: validation in the JPA converter (status quo) — an infrastructure adapter owning a domain rule (mild anemia); rejected pushing serialization into the VO — couples the domain to a storage format, breaking persistence-ignorance. (taste)
- D14: @aggregate @cqrs @ddd — a rich, root-coordinated write aggregate (cross-entity invariants like publish/takedown enforced by the root, not an app service); deliberately dumb read projections; `planner_stats` the sanctioned counter exception (atomic increments, not load-mutate-save). rejected: making every entity rich — read projections gain nothing from behavior and it misreads DDD; rejected an anemic aggregate with logic in services — scatters invariants across the service layer. (taste)
- D15: @dto @api @column-rename — column renames are internal (DTO/JSON wire names stay `schemaVersion`/`contentVersion`); `saved_at` dropped, DTO `savedAt` → `last_modified_at` (post-split `content.last_modified_at` only bumps on owner content writes, so it IS the save time); `device_id` → BINARY(16). rejected: renaming the DTO/JSON wire names — breaks the FE contract for a purely internal change; rejected keeping `saved_at` — redundant with `last_modified_at` once counter/moderation writes leave the content row. (default)
- D16: @planner_catalog @planner_type @index — `planner_catalog` carries `planner_type`, but the browse index `(first_published_at, planner_type, category)` leads with the sort key and keeps `planner_type`/`category` as trailing in-index (ICP) residuals; the recommended index `(recommended, first_published_at, planner_type, category)` leads with the selective `recommended`. rejected: type-leading `(planner_type, first_published_at, …)` — a 2-value seek is worthless and it filesorts the all-type query (today's only shape); rejected omitting `planner_type`/`category` from the index — forces a clustered-row fetch per candidate instead of an in-index ICP filter. (evidence: no query filters planner_type today; category cardinality = 3; V046 precedent)
- D17: @user-delete @cascade @app-sweep — user hard-delete does an app-side batch `DELETE` across every planner table (satellites, projections, filters, children) by the user's planner ids, then deletes the user; `fk_planner_user ON DELETE CASCADE` is the core backstop. rejected: relying on FK cascade to clean up — the satellites/projections have no FK (D1) and would orphan, including live `planner_catalog` rows. enforcement: row `user-delete-sweeps-aggregate`.

## Renames (code, to match the schema)
- Tables: `planner_content_index` → `planner_entity_filter`; new `planner_keyword_filter`.
- Entities: `PlannerContentIndex` → `PlannerEntityFilter` (+ `…Id`); new `PlannerKeywordFilter`; new `PlannerContent`/`PlannerPublication`/`PlannerModeration`/`PlannerCatalog`; `PlannerStats` extended.
- Repositories: `PlannerContentIndexRepository` → `PlannerEntityFilterRepository`; new `PlannerKeywordFilterRepository`, `PlannerContentRepository`, `PlannerPublicationRepository`, `PlannerModerationRepository`, `PlannerCatalogRepository`.
- Services: `PlannerIndexService` → `PlannerFilterService` (maintains BOTH filter tables); new `PlannerCatalogService` (single catalog choke point); new scheduled reconciler.
- Methods: `reindex(id)` → `rebuildFilters(id)`; `deleteIndex(id)` → `clearFilters(id)`; spec `hasContentEntity(type,id)` → `containsEntity(type,id)`.
- Value object: new `PlannerKeywords` (`fromClient`/`fromStorage`).
- Columns: `version` → `row_lock_version`; `schema_version` → `content_schema_version`; `content_version` → `game_content_version`; drop `saved_at`; `device_id` VARCHAR(255) → BINARY(16).

## Maintenance & read map
Write fan-out (event → table op, via the maintenance service):

| Event | content | publication | moderation | stats | catalog | entity_filter | keyword_filter |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| create | INS | INS | INS | INS·0 | — | — | — |
| upsert draft | UPD | — | — | — | — | — | — |
| upsert published | UPD | — | — | — | UPD (sync) | rebuild† (async) | rebuild† (async) |
| publish | (UPD) | UPD | — | — | INS (sync) | rebuild (async) | rebuild (async) |
| unpublish / delete / takedown | (deleted_at) | UPD·pub=F | (takedown) | — | DEL (sync) | clear (async) | clear (async) |
| hide / unhide | — | — | UPD | — | UPD·recommended | — | — |
| vote (crossing) | — | — | — | UPD·upvotes,notified | UPD·recommended | — | — |
| view | — | — | — | UPD·view_count (async flush) | — | — | — |
| comment ± | — | — | — | UPD·comment_count | — | — | — |
| user account deleted | DEL* | DEL* | DEL* | DEL* | DEL* | DEL* | DEL* |

† filter rebuild skipped when content composition unchanged (title-only edit). Catalog scalar copy is
SYNCHRONOUS; filter rebuild is AFTER_COMMIT (REQUIRES_NEW), batched. `*` user-delete = app-side batch
`DELETE` by the user's planner ids across all tables (+ children), then delete the user (D17).

Read join-map (public reads never touch `content`; projection reads never touch the write aggregate):
- list (type-scoped): `catalog`(type filter/recency) ⨝ `stats`(counts) ⨝ `planner→users`(author)
- recommended: `catalog`(type, recommended, recency) ⨝ `stats` ⨝ `planner→users`
- faceted search: `catalog`(title ngram-FULLTEXT / category, OR keyword EXISTS) ⨝ EXISTS `entity_filter` ⨝ EXISTS `keyword_filter`
- detail: `planner` + `content` + `publication` + `stats` + `moderation` (recommended + modified-but-not-published computed live)
- owner list (`GET /api/planner/md`): `planner` ⨝ `content` ordered by `content.last_modified_at` — filesort (INV6 forbids an index, not a filesort; per-user cardinality small)
- sync-pull (owner, per-id): `planner` + `content`

## Invariants
- INV1: A taken-down planner cannot be republished — verify: row `takedown-blocks-republish`.
- INV2: A concurrent same-row owner write never surfaces a 503 deadlock — verify: row `concurrent-upsert-yields-409` + spike SP1.
- INV3: `planner_stats` counters equal the authoritative child aggregates (upvotes↔votes, comment_count↔comments) — verify: reconciler drill.
- INV4: `planner_catalog` membership == published ∧ ¬deleted ∧ ¬taken_down; `catalog.recommended` == upvotes ≥ thr ∧ ¬hidden — verify: reconciler drill.
- INV5: Reading any persisted keyword row never throws (`fromStorage` totality) — verify: row `keyword-read-passthrough-total`.
- INV6: `planner_content` (owner-mutated) has no inbound FK and no secondary index — verify: schema drill.
- INV7: `status` ∈ {draft, saved} is the EDIT state, orthogonal to `published` — a value never encodes publication. "Modified-but-not-published" is `published ∧ status-dirty` (computed across `content` ⨝ `publication`), never a stored column; the FE derives it from the detail response — verify: row `unpublished-changes-visible-to-owner`.

## Behavior Inventory (brownfield — seams rewired)
| # | Seam | Observable behavior (as-is) | Verdict |
|---|---|---|---|
| 1 | upsert conflict | 409 CONCURRENT_WRITE via optimistic lock; intermittent 503 on InnoDB deadlock | preserve 409; DROP the 503 |
| 2 | view count | dual-write `planners` + `planner_stats`, flag-gated read, async buffer+flush | preserve async increment; DROP dual-write → stats only |
| 3 | list sort | `recent` (createdAt) / `popular` / `votes`; recommended default = `votes` | preserve `recent` (now `first_published_at`); DROP `popular`/`votes` + the `sort` param; recommended default order becomes recency |
| 4 | search `q` | `LOWER(title) LIKE %q%` OR `LOWER(selected_keywords) LIKE %q%` (substring, non-sargable) | DROP substring-LIKE; replace: ngram FULLTEXT on title OR `planner_keyword_filter` EXISTS (keyword match preserved) |
| 5 | keyword storage | MySQL `SET(...)` + `ALTER MODIFY` per keyword; `FIND_IN_SET` filter | DROP SET → `JSON` display + `planner_keyword_filter` inverted index |
| 6 | publish | 2 requests (sync draft, then toggle published) | DROP two-request; consolidate to one atomic request |
| 7 | recommended | computed per-row (`upvotes ≥ thr ∧ ¬hidden`) in list queries | preserve semantics; materialize as `catalog.recommended` for list, compute live for detail |
| 8 | detail/owner-list DTOs | fields incl deviceId/savedAt/schemaVersion/contentVersion | preserve field-for-field (internal renames only) |
| 9 | public list DTO | includes `contentVersion` + `lastModifiedAt` | DROP both from the list card (deliberate trim); card shows `first_published_at` |
| 10 | user hard-delete | relies on `fk_planner_user ON DELETE CASCADE` to remove planners | preserve deletion; app-side batch sweep across all planner tables (satellites have no FK) |

## Done When
- [ ] Concurrent upserts serialize to 409, never 503 (containerized-it) — R2
- [ ] Concurrent votes never 503; counts correct (containerized-it) — R2
- [ ] All counter reads/writes hit `planner_stats`; `planners` counter columns dropped (containerized-it) — R3
- [ ] `comment_count` maintained (inc/dec) + reconciler detects seeded drift across stats/comment/catalog/filters/recommended (containerized-it) — R3, R8
- [ ] Catalog visible-only + recency sort + ngram-FULLTEXT title (incl a CJK title case) (containerized-it) — R4
- [ ] `q` matches title OR keyword; boolean operators escaped (containerized-it) — R4
- [ ] Faceted search via `planner_entity_filter` + `planner_keyword_filter` (containerized-it) — R5
- [ ] Keyword JSON pass-through read + rename-normalized-on-write (containerized-it) — R6
- [ ] Published title edit consistent across list + detail; publish is one request (containerized-it) — R7
- [ ] User hard-delete sweeps all planner tables; no orphans (containerized-it) — R1
- [ ] Detail/owner-list DTOs field-identical; public list card trims contentVersion+lastModifiedAt; `sort` param removed (it) — R9
- [ ] Full backend suite green; `ddl-auto=validate` passes against the migrated schema; ngram config set in the `it` profile (local-tdd)
- [ ] Query-count assertions added on list/recommend/search before their rewrite (local-tdd)

## Migrations
Stop-the-world (server down) — no online cutover, so no dual-write, no read flag, no gradual window.
One migration run against a stopped app, ordered:
1. Create `planner` (core) + `planner_content`/`publication`/`moderation`/`catalog`(with `planner_type`)/`entity_filter`/`keyword_filter`; extend `planner_stats` with `comment_count` + `recommended_notified_at`.
2. Backfill (existing published + draft data preserved):
   - Scalar copies from `planners` into the aggregate tables; `selected_keywords` SET-CSV → JSON array (renames applied); `game_content_version` from `content_version`; `last_modified_at` as-is; `device_id` via `UUID_TO_BIN` guarding non-UUID rows (NULL them).
   - `planner_stats.comment_count` ← `COUNT(*)` over `planner_comments WHERE deleted_at IS NULL` grouped by planner; `recommended_notified_at` from `planners`.
   - `planner_catalog` seeded ONLY for visible rows (published ∧ ¬deleted ∧ ¬taken_down), with `planner_type` + `recommended` computed (`upvotes ≥ threshold ∧ ¬hidden_from_recommended`).
   - `planner_entity_filter` + `planner_keyword_filter`: rebuilt via a one-shot app-side `PlannerFilterService.rebuildFilters` pass over visible planners (parity with the runtime JSON paths — `selectedGiftIds`/`observationGiftIds`/`comprehensiveGiftIds`/`floorSelections[*]`/identities/egos across MD/RR + schema_version 1), NOT hand-rolled SQL. Keywords derive from the (now-normalized) `selected_keywords`. entity_id guarded `REGEXP '^[0-9]+$'`. Scope: visible planners only (matches the clear-on-unpublish maintenance semantics).
3. Repoint child-table FKs (`planner_votes`/`views`/`comments`/`bookmarks`/`subscriptions`/`reports`) from `planners(id)` to `planner(id)`, or drop them for the app-side sweep. Drop `fk_planner_stats_planner` (V048, → `planners`) and the old `planner_content_index` table (V039, replaced by `planner_entity_filter`).
4. Drop the moved columns / the old `planners` table.
MySQL DDL is non-transactional, so "one deploy" is not one transaction: make each step rerunnable (IF NOT EXISTS / INSERT IGNORE) and verify row-count parity (aggregate vs source, filters vs a rebuild dry-run) BEFORE the step-4 drops. Existing user data is preserved (step-2 backfill), not drop-and-recreate. The smoke-test seed `backend/src/test/resources/db/seed/migration-test-seed.sql` is updated to the new schema in the same change.

## Deferred
- GA → Cloudflare LB + Tunnels + method-based write-routing (writes → Oregon primary) — cross-region write latency (~1-2s for Asian users) persists until done; separate infra task.
- `device_id` column drop — pending frontend confirmation that `response.deviceId` is unread; kept as BINARY(16) meanwhile.
- `planner_document` blob split — dissolved (autosave is client-local; every content read wants the JSON); revisit only if server write-I/O bites.
- Split title into its own FULLTEXT table — only if profiling shows the FULLTEXT index dragging browse performance.
- Takedown restore / un-takedown path — none exists today; takedown is currently permanent. If added, the maintenance matrix needs a `catalog INS` on restore.
- FTS deleted-doc reclamation — ngram INSERT/DELETE churn retains deleted docs until `OPTIMIZE TABLE` (with `innodb_optimize_fulltext_only`); an ops concern, not this task.
- Keyset pagination — list endpoints use OFFSET + a per-request `COUNT(*)` (Spring `Page`); fine at catalog scale, but deep OFFSET + a tight filter degrades linearly (scan-and-discard). Switch to keyset (`first_published_at < :last_seen`, composes with `idx_catalog_recent`) or `Slice` if the catalog grows large.

## Amendments
<!-- append-only, empty at first. A row found wrong mid-build is amended HERE, re-gated by the user. -->
- A1: Migrations step 2, filter backfill — SUPERSEDES "one-shot app-side
  `PlannerFilterService.rebuildFilters` pass ... NOT hand-rolled SQL". The
  entity/keyword filter backfill is a plain SQL migration using `JSON_TABLE`
  over the same JSON paths the runtime extractor reads
  (`equipment.*.identity.id`, `equipment.*.egos.*.id`, the three gift-id
  arrays, `floorSelections[*].giftIds`/`themePackId`), with the
  `REGEXP '^[0-9]+$'` entity-id guard and keywords from the (rename-applied)
  `selected_keywords` JSON. Rationale: all-`.sql` migrations keep the whole
  chain executable by the CI migration smoke gate (a Java migration is
  invisible to it), and runtime parity is enforced by the drift reconciler's
  filter check instead of code-path identity.

## Runner
- Framework: JUnit 5 + Testcontainers (MySQL/Redis) on the `it` profile, tagged `@Tag("containerized")`.
- Full suite (incl. containerized): `/home/user/github/LimbusPlanner/backend/gradlew -p backend test`
- Unit-only (no Docker): `/home/user/github/LimbusPlanner/backend/gradlew -p backend test -PexcludeTags=containerized`
- The `it`-profile MySQL container must set the ngram parser available (default in MySQL 8; `ngram_token_size=2` is the default — no override needed) so FULLTEXT tests match prod.
- Quirk: the gradle wrapper is at `backend/gradlew` (NOT the repo root — the CLAUDE.md path is stale). A bare `gradlew` at repo root is hook-blocked. Confirm the `BUILD SUCCESSFUL`/`FAILED` line + fresh test XML, not the exit code alone.
