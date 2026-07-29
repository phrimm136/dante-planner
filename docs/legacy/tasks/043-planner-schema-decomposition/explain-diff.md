# explain-diff: planner god-table decomposition

_Range: `task/043-planner-schema-decomposition-base..HEAD` ·  110 files changed · generated cold from the diff._

## Background

### What this codebase is

Dante's Planner is a web app for planning and sharing team/build setups for the mobile game *Limbus Company*. Players assemble a "planner" — a JSON document describing the identities, E.G.O.s, E.G.O. gifts, theme packs, and keywords they'll run in a Mirror Dungeon or Refracted Railway run — save it privately, and optionally publish it so other players can browse, search, upvote, bookmark, and comment on it.

The stack is a React/TypeScript frontend (TanStack Query, shadcn/ui) over a Spring Boot + Java backend, with static game data shipped as JSON. The backend is organized into feature packages under `org.danteplanner.backend`:

- **planner** — the core CRUD, publishing, search, and view/vote counters (the slice this change rewrites)
- **comment**, **moderation** — public engagement and moderator takedown/hide actions
- **user**, **auth** — accounts and login
- **shared** — cross-cutting config, entity enums, utilities

Authentication is OAuth-only: there are no passwords. A successful OAuth login mints a JWT delivered as an HTTP cookie; every request identity is a `userId` extracted from that cookie. This matters for the planner slice because almost every endpoint has two audiences — the **owner** of a planner and the **public** — and the split is drawn by whether a valid owner `userId` is present.

### Persistence and the test tiers

Schema is owned entirely by **Flyway** migrations (`backend/src/main/resources/db/migration/V###__*.sql`); the database is never altered by hand and `spring.jpa.hibernate.ddl-auto` is `none` in production. Migrations are immutable once merged — a mistake is corrected by a *new* migration, never an edit.

There are three test tiers, and the middle one constrains the entire design:

- **unit / `test` profile** — H2 or mocks, `ddl-auto=create-drop`.
- **integration / `it` profile** — real MySQL via **Testcontainers**, with `ddl-auto=validate`. Hibernate boots against the actual Flyway-migrated schema and refuses to start if any entity's mapping drifts from the real columns. This is why entities carry annotations like `@JdbcTypeCode(SqlTypes.CHAR)` on ENUM/SET columns (MySQL reports them to JDBC as `CHAR`), and why a schema change and its entity change must land together — you cannot merge one without the other or the `it` suite fails to boot.
- A **smoke-test seed** (`backend/src/test/resources/db/seed/migration-test-seed.sql`) is run through each new migration in CI, so any migration that touches column shapes or SET/ENUM membership must update the seed in the same change.

### The owner-vs-public endpoint split

All planner HTTP endpoints live under `/api/planner/md`, fanned across several controllers. Conceptually there are two read models over the same data:

- **Owner (command) side** — create/upsert/update/delete/import of a user's own planners. The owner sync path is idempotent: the client PUTs a client-generated UUID with a `syncVersion` for optimistic-concurrency conflict detection, and the server upserts.
- **Public (query) side** — `PublishedPlannerController` / `PublishedPlannerQueryService` serve the published catalog: paginated published lists (optionally by category), a "recommended" subset (upvotes over a threshold, not hidden by a moderator), full-text-ish search, and single-planner detail with view recording.

Before this change, both read models were served from **one table**.

### The touched slice before the change: the `planners` god-table

Everything about a planner lived in a single wide row. `Planner.java` (the pre-change entity, `@Table(name = "planners")`) carried, on one row:

- **Identity / core**: `id` (client-supplied `BINARY(16)` UUID), `user`, `plannerType`, `createdAt` — write-once facts.
- **Content**: `title`, `category`, `status`, `content` (a `JSON` blob), `schemaVersion`, `contentVersion`, `syncVersion`, `deviceId`, `lastModifiedAt`, `savedAt`, `deletedAt` (soft delete) — the hot owner-write fields, rewritten on every save.
- **Publication**: `published`, `firstPublishedAt`, `ownerNotificationsEnabled`.
- **Moderation**: `takenDownAt`, `hiddenFromRecommended`, `hiddenByModeratorId`, `hiddenReason`, `hiddenAt`.
- **Counters**: `upvotes`, `viewCount`, plus `recommendedNotifiedAt` (a once-only notification stamp) and a JPA `@Version` optimistic-lock column.
- **Search**: `selectedKeywords`, a MySQL **SET** column persisted as a comma-separated string through `KeywordSetConverter`.

Two satellite tables hung off it by foreign key:

- `planner_content_index` (migration V039) — an inverted index of `(entity_type, entity_id, planner_id)` extracted from the content JSON, rebuilt on every publish-save by `PlannerIndexService.reindex()` (delete-all-then-reinsert for that planner). `FK fk_pci_planner → planners(id) ON DELETE CASCADE`.
- `planner_stats` (migration V048) — a newer, still-in-flight counter table (see below).

<pre>
                    ┌──────────────────────────────────────────┐
                    │                planners                   │  one wide row
                    │  core | content | publication | moderation│  = every concern
                    │  counters | selected_keywords (SET)       │
                    └──────────────────────────────────────────┘
                         ▲ FK              ▲ FK            ▲ FK...
              planner_content_index   planner_stats   votes/views/comments/…
</pre>

#### Why concurrent owner saves could deadlock

The publish-save path, inside a single `@Transactional`, both **UPDATEs the `planners` row** (the save bumps `syncVersion`/`lastModifiedAt` and the `@Version` column, taking an eventual **X** lock on that row) and **INSERTs child rows into `planner_content_index`**. Because those child rows carry an FK to `planners(id)`, each insert makes InnoDB take a **shared (S)** lock on the parent `planners` row to check referential integrity. When two saves of the *same* planner run concurrently, both can hold the S lock from their FK-child inserts and then each try to upgrade to the X lock for the row UPDATE — a classic **S→X upgrade deadlock**, surfacing as InnoDB deadlock rollbacks. (A recent commit works around it by retrying the upsert past deadlocks; the read path already serialized concurrent view-count writes with an explicit `SELECT … FOR UPDATE`, `findByIdForUpdate`, for the same reason.)

#### LIKE-based search over a SET column

Public search matched a term against title **or** keywords with SQL `LIKE '%term%'` over `LOWER(title)` and `LOWER(selected_keywords)` — i.e. a substring scan of the raw comma-separated SET string (see `PlannerRepository.findPublishedWithSearch` and friends, one query variant per {plain/category} × {published/recommended} combination). Exact keyword *filtering* used MySQL `FIND_IN_SET` on the same column (`PlannerSpecifications.hasKeyword`), and entity filtering used `EXISTS` subqueries against `planner_content_index` (`hasContentEntity`). Because the SET stores stable internal ids (e.g. `"9828"`, `"Combustion"`), the LIKE search never matched a user's typed game-term; and `LIKE`/`FIND_IN_SET` on a plain column cannot use an index. Lists, recommended, and search were served by `JOIN FETCH p.user` queries whose ordering came from the Spring `Pageable`'s sort params.

#### The in-flight `planner_stats` dual-write

A counter cutover was already partly staged. `planner_stats` (V048, one row per planner, `view_count`/`upvotes`) was being **dual-written**: `PlannerViewRecorder.flush()` incremented *both* `planners.view_count` and `planner_stats.view_count`. Which value was *read* was gated by `StatsReadsFlag`, a runtime `AtomicBoolean` seeded from `planner.stats.reads-enabled` (default off). With the flag off, the detail view served the legacy `planners.view_count`; with it on, it served `planner_stats` (falling back to the legacy value if the stats row was missing). Only the single-planner detail read consulted the flag — list/search/recommended counters still came off the `planners` columns.

### Constraints visible in the code

- **A frozen frontend wire contract.** The public/owner DTOs (`PublicPlannerResponse`, `PublishedPlannerDetailResponse`, `PlannerResponse`) are Java records whose field names are the JSON keys the shipped React client already consumes — `selectedKeywords`, `upvotes`, `viewCount`, `commentCount`, `authorUsernameEpithet`, `hasUpvoted`, `isBookmarked`, and so on. The persistence layer underneath can be reshaped freely, but these serialized names cannot move without breaking clients in the field.
- **A stop-the-world migration posture.** MySQL DDL is non-transactional and the app treats migrations as a single ordered forward-only chain; there is no online/ghost-table tooling in play. So a table restructuring has to be expressed as guarded, individually-rerunnable steps (`CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE`, information-schema existence guards before every `ALTER`/`ADD FOREIGN KEY`), because a mid-migration crash must be safe to replay from the top.
- **A Korean user base.** The audience is largely Korean-speaking, so title search needs to match CJK text — which byte-substring `LIKE` over a Latin-oriented column handles poorly. (The new schema introduces a `FULLTEXT … WITH PARSER ngram` index, MySQL's CJK-capable full-text tokenizer, precisely for this.)
- **Cross-region write latency.** The counter and view machinery is deliberately asynchronous: views are buffered per-pod (`PlannerViewRecorder`) and flushed every 500 ms with same-day dedup on `(planner, viewer, day)` rather than written synchronously on each request, and there is an integration test for replica lag (`ReplicaLagIT`). These patterns signal a deployment where write round-trips are expensive and the design keeps them off the request hot path.

## What is implemented

The single `planners` god-table is gone. In its place is a small constellation of tables split along two axes: **who writes them** and **who reads them**. Everything the old row carried is preserved; what changed is that mutable concerns no longer share a physical row, and the public browse path no longer reads owner-write tables at all.

### The new persistence shape

There are three families of tables.

**The write aggregate** — the source of truth an owner mutates:

- `planner` — the write-once *core*: `id`, `user_id`, `planner_type`, `created_at`. Nothing on this row is ever updated after insert. It is the only table an inbound foreign key is allowed to point at (besides `users`).
- `planner_content` — the owner hot-path *satellite*, PK-shared with the core (`@MapsId`): title, status, category, the `content` JSON document, the `selected_keywords` JSON array, schema/game-content versions, the client-facing `sync_version`, the JPA optimistic-lock `row_lock_version`, `device_id`, `last_modified_at`, and the `deleted_at` soft-delete stamp. It deliberately carries **no secondary index and no inbound FK**.
- `planner_publication` — the publish-lifecycle satellite: `published`, `first_published_at`, `owner_notifications_enabled`.
- `planner_moderation` — the moderator satellite: `taken_down_at`, `hidden_from_recommended`, and the hide audit trail (`hidden_by_moderator_id` with `ON DELETE SET NULL`, reason, timestamp).

The `Planner` entity is the JPA aggregate root: it holds the three satellites as PK-shared one-to-ones and fronts them with delegating readers (`getTitle()` reads `content.title`, `getPublished()` reads `publication.published`, and so on), so callers still see one object while the storage is decomposed. It implements Spring Data `Persistable` with a transient `isNew` flag so client-assigned UUIDs take the batchable INSERT path instead of merge's SELECT-then-INSERT. Cross-entity invariants live on the root: `takeDown()` stamps moderation *and* unpublishes; `togglePublished()` refuses to republish a taken-down planner.

**Counters** live on their own table, `planner_stats` (PK-shared, no FK): `view_count`, `upvotes`, the newly-added `comment_count`, and the `recommended_notified_at` CAS stamp that moved off the god-table.

**The read projections** — maintained by the write side, read by the public paths:

- `planner_catalog` — the visible-only browse projection. A row exists *exactly while* the planner is published AND not deleted AND not taken down. It holds scalar copies (type, category, title, keywords, `first_published_at`, a derived `recommended` flag) and carries the two browse indexes plus an **ngram FULLTEXT index on `title`**.
- `planner_entity_filter` — an inverted index `(entity_type, entity_id, planner_id)` mapping each content entity (identity, EGO, EGO gift, theme pack; all ids are integers) to the visible planners containing it. FK to the core, `ON DELETE CASCADE`.
- `planner_keyword_filter` — an inverted index `(keyword, planner_id)` mapping each selected keyword to the visible planners carrying it. FK to the core, `ON DELETE CASCADE`.

**Which path touches which table.** An owner's create/upsert/update writes the core + satellites + `planner_stats`; owner list reads project over `planner ⨝ planner_content`. Public *browse and search* read `planner_catalog` and the two filter tables only — never the owner-write rows — with counters joined from `planner_stats` and author fields batch-loaded via a `PlannerCoreInfo` projection over the core. The single-planner *detail* read still loads the full write aggregate (it needs the content document). View/vote/comment writes hit `planner_stats` as atomic upserts. Membership of the projection tables is owned entirely by the write side; nothing reconstructs them on read.

### The four-step Flyway cutover (V049–V052)

The decomposition ships as four migrations that run against a populated `planners` table. Every statement is written to be rerunnable, because MySQL DDL is non-transactional and a failed migration must be safe to re-execute.

- **V049 — create.** Creates the six new tables and extends `planner_stats` with `comment_count` and `recommended_notified_at` (guarded with `information_schema` checks, since MySQL has no `ADD COLUMN IF NOT EXISTS`). The core's user FK is created under a temporary name `fk_planner_user_core`, because FK names are schema-global and the legacy `planners` table still holds `fk_planner_user` until V052.
- **V050 — backfill the aggregate.** `INSERT IGNORE` copies every legacy row (drafts and soft-deleted included) into the core and the three satellites. `planner_stats` is upserted from the legacy counter columns as the authoritative values, then `comment_count` is set from a live-comment count. `planner_catalog` is seeded **only for visible rows** (`published AND deleted_at IS NULL AND taken_down_at IS NULL`), with `recommended` derived from `upvotes >= 10 AND NOT hidden`. Legacy keyword renames are applied inline here (`AccelBullet`→`9828`, `ChargeLoad`→`EmergencyChargeForceField`).
- **V051 — filter backfill via `JSON_TABLE`.** Rebuilds both inverted indexes for every visible planner by reading the *same* JSON paths the runtime extractor indexes — `equipment.*.identity.id`, `equipment.*.egos.*.id` (object-keyed egos only), the three top-level gift-id arrays, `floorSelections[*].giftIds`, and `floorSelections[*].themePackId` — with a `REGEXP '^[0-9]+$'` guard dropping non-integer ids. Keywords come from the rename-applied `selected_keywords` array.
- **V052 — cutover.** Repoints the child-table FKs (`planner_votes`, `planner_views`, `planner_comments`, `planner_bookmarks`, `planner_subscriptions`, `planner_reports`) from `planners(id)` to `planner(id)` via a guarded stored procedure, and drops `planner_stats`' FK entirely (counters are swept app-side). Then a **parity assertion runs before any drop**: it `SIGNAL`s (aborting the migration while source rows are still intact) unless every aggregate table has the same row count as `planners`, `planner_stats` covers every planner, catalog membership equals the visible-planner count, and the stats counter sums match the legacy counter sums. Only when parity holds does it drop `planner_content_index` and `planners`, and rename the core FK to its canonical `fk_planner_user`.

### Concurrency: owner saves cannot deadlock; counters are atomic

The old god-table was a single row that owner edits, counter bumps, and FK-bearing children all contended on. The decomposition removes that. `planner_content` — the only row an owner save mutates — carries no secondary index and no inbound FK (invariant "INV6"), so a concurrent same-row save can only serialize on that row's exclusive lock. It can never deadlock by acquiring a child's shared lock in the opposite order, because no child points at it. FKs point *only* outward at `users` or at the write-once core, which by definition is never updated.

Counter traffic never load-mutate-saves. `PlannerStatsRepository` exposes every counter write as a native `INSERT ... ON DUPLICATE KEY UPDATE x = x + :delta` (views, upvotes, comment count) or a bounded `UPDATE` (comment decrement floored at zero; the recommended-notified CAS). Each takes only the `planner_stats` row lock and is safe under concurrency, so a burst of viewers or voters cannot contend with the owner editing content.

### Search: title FULLTEXT OR keyword-index, integer facets, recency-only

Public search reads the catalog via composable JPA `Specification`s (`CatalogSpecifications`). Because catalog *presence is visibility*, no predicate checks published/deleted/taken-down.

- **Free-text `q`** matches a planner if the ngram-parsed FULLTEXT `title` search hits **OR** the term exactly matches a row in the keyword index. The FULLTEXT operator is surfaced to Criteria as a custom HQL function `match_against(col, query)` registered by `MySqlFunctionContributor` (via a `META-INF/services` `FunctionContributor`), expanding to `MATCH (col) AGAINST (query IN BOOLEAN MODE)`. Raw input is neutralized into a boolean-mode query by quoting each whitespace-separated term as a phrase and OR-combining them, so user-typed operators can't inject FULLTEXT syntax. (ngram token size is 2, so a one-character term matches no title.)
- **Keyword facets** (`keyword=`) are exact membership tests against `planner_keyword_filter` via `EXISTS` subqueries — one AND-composed per keyword.
- **Entity facets** (`identity` / `ego` / `gift` / `themePack`) are integer-id `EXISTS` tests against `planner_entity_filter`, AND-composed; an unparseable id resolves to `-1` and matches nothing.
- **Ordering is recency-only.** The `sort` parameter is gone. List queries pin `ORDER BY first_published_at DESC` in query text (riding `idx_catalog_recent` / `idx_catalog_recommended`); Specification searches force a `firstPublishedAt DESC` pageable and strip any caller-supplied sort.

### The keyword domain value

Keyword membership and legacy-name normalization are a domain value, `PlannerKeywords`, not persistence plumbing. It holds `VALID_KEYWORDS` (the authoritative id list) and a `RENAME_MAP` for pre-rename client ids (`AccelBullet`→`9828`, `ChargeLoad`→`EmergencyChargeForceField`). `fromClient(...)` remaps renamed ids, keeps valid members, and collects the rest as `dropped()` for the caller to log — it drops unknowns rather than rejecting, so a stale client can still sync. `fromStorage(...)` is total: whatever was persisted reads back as the current valid subset, never an error.

Normalization happens at the **domain boundary in the command service** (`applyRequestFields` / `buildAggregate` call `PlannerKeywords.fromClient(...).asSet()`), so the entity — and everything fed from it (the JSON column, the filter index, the facets) — only ever carries current ids. `KeywordSetConverter` is a thin JSON format adapter: it serializes/deserializes the `Set<String>`, re-runs normalization as a safety net, and logs drops; it owns no rules.

### Publish flows

Publishing a draft is now **one content-carrying request**. `PUT /api/planner/md/{id}/publish` with a body calls `publishWithContent`, which upserts the carried document (creating the planner if it never synced) and then ensures it is published — both halves in one transaction, so a stale-content conflict or a takedown rejection rolls back the whole thing. With no body, the same endpoint stays the lightweight toggle used for unpublish.

**All visibility transitions are owned by `PlannerCatalogService` intents**, so no caller wires catalog-plus-filter maintenance by hand:

- `onBecameVisible(planner)` — insert the catalog row (computing `recommended` from current stats + moderation state) and request a filter rebuild.
- `onBecameInvisible(plannerId)` — delete the catalog row and request a filter clear. Used by unpublish, owner soft-delete, and moderator takedown alike.
- `onVisibleEditCommitted(planner, compositionChanged)` — synchronize the catalog scalar copies (so a title edit shows in the list immediately) and rebuild the filter indexes **only when the searchable composition changed**. The command service computes `compositionChanged` as "content or keyword set actually differs," so a pure metadata save skips the index churn.
- `refreshRecommended(plannerId)` — recompute the derived flag on vote-threshold crossings and moderation hide/unhide.

Filter maintenance itself is deferred. `PlannerFilterService.requestRebuild/requestClear` publish an application event; the listener runs **`AFTER_COMMIT` in a `REQUIRES_NEW` transaction**, keeping the multi-statement index work out of the owning (cross-region) write path. A rebuild deletes both filter sets for the planner, re-extracts entities from the content JSON through the shared `PlannerContentEntityExtractor`, and re-inserts keyword rows.

### The drift reconciler

`PlannerDriftReconciler` is a scheduled (nightly by default) read-only audit over the projections and counters. It checks `planner_stats.upvotes` against the vote-row count, `comment_count` against live comments, catalog membership against visibility (both directions), each filter index against a fresh extraction of the stored content (the same code path runtime maintenance uses), and the derived `recommended` flag. For every divergence it emits **one structured `DriftRecord`** as a log-warn plus a `planner_reconciler_drift_total` metric tagged by kind — and **repairs nothing**. Drift is treated as a maintenance bug to find and fix, not a table to quietly patch.

### User hard-delete sweep and wire contract

Because the projection, satellite, filter, and stats tables carry no FK to the planner core, permanent user deletion can't rely on cascade for them. `UserAccountLifecycleService.performHardDelete` reassigns votes/comments to the sentinel user, then explicitly sweeps — by the user's planner ids — the comment/planner reports, both filter tables, catalog, stats, moderation, publication, and content rows, and *then* deletes the user, letting the `users` cascade remove the write-once cores and the FK-bearing child tables.

The **wire contract is otherwise frozen**. The only public-facing change is a trim of the list card (`PublicPlannerResponse`): `contentVersion` and `lastModifiedAt` are dropped, and `firstPublishedAt` (the release date, the field the recency sort keys on) is added; content metadata now lives on the detail response only. The detail response and every other endpoint keep their existing shape.

### Worked example: publish "Bleed Team", search for it, take it down

An owner publishes a never-synced draft in one request: `PUT /api/planner/md/{id}/publish` with a body whose `title` is `"Bleed Team"` and `selectedKeywords` is `["AccelBullet"]`.

**1. Publish.** `publishWithContent` calls `upsertPlanner`. The planner doesn't exist, so `createPlanner` builds the aggregate. At the domain boundary, `PlannerKeywords.fromClient(["AccelBullet"])` remaps the legacy id through `RENAME_MAP` to **`"9828"`**, which is in `VALID_KEYWORDS` — so the content row's keyword set is `{"9828"}`. The rows written:

- `planner`: `(id, user_id, MIRROR_DUNGEON, created_at)`.
- `planner_content`: `title="Bleed Team"`, `status=DRAFT`, `selected_keywords=["9828"]` (serialized by the converter), the content JSON, `deleted_at=NULL`.
- `planner_publication`: `published=false`, `first_published_at=NULL`.
- `planner_moderation`: empty.
- `planner_stats`: `view_count=0, upvotes=0, comment_count=0`.

Back in `publishWithContent`, the planner isn't published yet, so `togglePublish` runs: `publication.toggle()` sets `published=true` and stamps `first_published_at`; the title is non-blank and content passes strict validation. Then `onBecameVisible` fires:

- `planner_catalog` row inserted: `title="Bleed Team"`, `selected_keywords=["9828"]`, `first_published_at=<stamp>`, `recommended=false` (0 upvotes < threshold 10).
- A filter rebuild is requested; **after the transaction commits**, in a new transaction, `planner_keyword_filter` gets `("9828", <plannerId>)`, and `planner_entity_filter` gets one row per integer entity id found in the content JSON.

**2. Search** for `q="eed"` with `keyword=9828`: `GET /api/planner/md/published?q=eed&keyword=9828`. The `keyword` param is present, so this routes to `searchPlanners` with `keywords=["9828"]`. The composed spec is `matchesQuery("eed") AND hasKeyword("9828")`:

- `matchesQuery("eed")` → `match_against(title, '"eed"') > 0 OR keyword="eed"`. The ngram parser tokenizes the 3-char term and the FULLTEXT index matches `"Bleed"` (it contains "eed"), so the title branch hits.
- `hasKeyword("9828")` → `EXISTS` in `planner_keyword_filter` where `keyword="9828"` and `planner_id` matches — hits the row inserted in step 1.

Both AND-clauses hold, the row is returned, ordered by `first_published_at DESC`. The card is assembled from the catalog row plus the `PlannerCoreInfo` author projection and the `planner_stats` counters.

**3. Takedown.** A moderator calls the takedown path → `ModerationService.deletePlanner` → `planner.takeDown()` stamps `moderation.taken_down_at` and unpublishes, then `onBecameInvisible`:

- the `planner_catalog` row is deleted, and
- after commit, the `planner_keyword_filter` row (`"9828"`) and the planner's `planner_entity_filter` rows are cleared.

The planner now matches nothing in browse or search. Its write-aggregate rows (`planner`, `planner_content`, `planner_stats`, `planner_publication`, `planner_moderation`) remain intact, so the owner can still sync their local copy — takedown removes it from the public projection, not from existence.

## The real diff

The change breaks a single `planners` god-table into a write-once **core** row plus purpose-built **satellite** and **projection** rows, then rewires the write and read paths around the new shape. The load-bearing property throughout is INV6: the owner-hot content row carries no secondary index and no inbound foreign key, so a same-row race can only serialize on one InnoDB row lock — it can never deadlock. Read in this order.

### 1. The target schema — V049

`V049__decompose_planners_create_tables.sql` creates every new table. Two rows matter most. The `planner` core is write-once (id, owner, type, creation time — nothing mutable), so any FK that points *here* never contends with an owner's save. The `planner_content` row holds everything the owner mutates and is deliberately bare:

```diff
+CREATE TABLE IF NOT EXISTS planner (
+    id            BINARY(16)  NOT NULL,
+    user_id       BIGINT      NOT NULL,
+    planner_type  ENUM('MIRROR_DUNGEON','REFRACTED_RAILWAY') NOT NULL,
+    created_at    DATETIME(6) NOT NULL,
+    PRIMARY KEY (id),
+    CONSTRAINT fk_planner_user_core FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
+) ENGINE=InnoDB ...;
+
+CREATE TABLE IF NOT EXISTS planner_content (
+    planner_id             BINARY(16)   NOT NULL,
+    title                  VARCHAR(255) NOT NULL DEFAULT 'Untitled',
+    ...
+    sync_version           BIGINT       NOT NULL DEFAULT 1,
+    row_lock_version       BIGINT       NOT NULL DEFAULT 0,
+    ...
+    PRIMARY KEY (planner_id)
+) ENGINE=InnoDB ...;
```

`planner_content` has **only** `PRIMARY KEY (planner_id)` — no secondary index, and no other table declares an FK toward it. That is the deadlock fix stated as DDL: a concurrent save of the same planner can only queue on that row's X lock, so InnoDB has no second lock to cross and no cycle to detect. The core FK is named `fk_planner_user_core` because MySQL FK names are schema-global and the legacy `planners` table still owns `fk_planner_user` until V052 drops it.

The projection tables carry their own indexes because they are read, not owner-written: `planner_catalog` gets the two recency indexes plus an ngram `FULLTEXT INDEX ftx_title`, and the inverted-index tables `planner_entity_filter` / `planner_keyword_filter` are keyed on natural composite PKs with an FK back to the *core* (never to content). `planner_stats` is extended in place, guarded because MySQL has no `ADD COLUMN IF NOT EXISTS`:

```diff
+SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
+    WHERE table_schema = DATABASE() AND table_name = 'planner_stats' AND column_name = 'comment_count');
+SET @ddl = IF(@col_exists = 0,
+    'ALTER TABLE planner_stats ADD COLUMN comment_count INT NOT NULL DEFAULT 0',
+    'SELECT 1');
+PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

Every statement is written to be rerunnable because MySQL DDL is non-transactional — a half-applied migration must survive re-execution.

### 2. The backfill — V050 / V051 / V052

`V050__decompose_planners_backfill.sql` copies legacy rows onto the aggregate with `INSERT IGNORE` (keyed on the shared PK, so reruns are no-ops). The interesting transform is the legacy `selected_keywords` column: it was a raw comma-separated string, and it becomes a JSON array *with keyword renames applied inline*:

```diff
+    CASE WHEN selected_keywords IS NULL OR selected_keywords = '' THEN NULL
+         ELSE CONCAT('["', REPLACE(REPLACE(REPLACE(selected_keywords,
+              'AccelBullet', '9828'),
+              'ChargeLoad', 'EmergencyChargeForceField'),
+              ',', '","'), '"]')
+    END,
```

Stats are treated as authoritative-from-legacy rather than trusting any drifted dual-write value — the upsert overwrites:

```diff
+INSERT INTO planner_stats (planner_id, view_count, upvotes, comment_count, recommended_notified_at)
+SELECT id, view_count, upvotes, 0, recommended_notified_at
+FROM planners
+ON DUPLICATE KEY UPDATE
+    view_count = VALUES(view_count),
+    upvotes = VALUES(upvotes),
+    recommended_notified_at = VALUES(recommended_notified_at);
```

`comment_count` is then filled from a live-comment count join, and `planner_catalog` is seeded **only for visible rows** — the `WHERE published = TRUE AND deleted_at IS NULL AND taken_down_at IS NULL` clause encodes "catalog membership == visibility," the same invariant the runtime maintains.

`V051__backfill_planner_filters.sql` rebuilds the inverted indexes by reading the *same JSON paths the runtime extractor indexes*, via `JSON_TABLE`. One statement is representative of all seven:

```diff
+INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id)
+SELECT 'IDENTITY', jt.id, c.planner_id
+FROM planner_content c
+JOIN planner_publication pub ON pub.planner_id = c.planner_id
+JOIN planner_moderation m ON m.planner_id = c.planner_id
+JOIN JSON_TABLE(JSON_EXTRACT(c.content, '$.equipment.*.identity.id'),
+                '$[*]' COLUMNS (id VARCHAR(20) PATH '$')) jt
+WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
+  AND jt.id REGEXP '^[0-9]+$';
```

The `REGEXP '^[0-9]+$'` guard drops non-numeric ids (entity ids are integers by contract); the visibility triple-join keeps the backfill scoped exactly to what the runtime would index. The remaining statements repeat this shape for object-keyed EGOs, the three top-level gift arrays, per-floor `NESTED PATH` gift ids, theme packs, and finally keywords from the rename-applied `selected_keywords` array.

`V052__decompose_planners_cutover.sql` does the dangerous part in a safe order: **repoint FKs → assert parity → only then drop**. Child-table FKs move from `planners(id)` to the write-once `planner(id)` via a procedure whose two guards make a crash between the DROP and the ADD rerun cleanly:

```diff
+CALL planner_decompose_repoint('planner_votes',    'fk_vote_planner',    TRUE);
+CALL planner_decompose_repoint('planner_views',    'fk_view_planner',    TRUE);
+CALL planner_decompose_repoint('planner_comments', 'fk_comment_planner', TRUE);
+...
```

`planner_stats` keeps **no** FK on purpose — counters are swept app-side, matching the invariant that FKs point only at `users` or the write-once core. The parity assertion runs *before* any drop, so a failed check aborts the migration while source rows are still intact:

```diff
+CREATE PROCEDURE assert_planner_decomposition_parity()
+BEGIN
+    IF EXISTS (SELECT 1 FROM information_schema.tables
+               WHERE table_schema = DATABASE() AND table_name = 'planners') THEN
+        IF (SELECT COUNT(*) FROM planners) <> (SELECT COUNT(*) FROM planner) THEN
+            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner core count != planners count';
+        END IF;
+        ...
+        IF (SELECT COUNT(*) FROM planner_catalog) <>
+           (SELECT COUNT(*) FROM planners
+            WHERE published = TRUE AND deleted_at IS NULL AND taken_down_at IS NULL) THEN
+            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: planner_catalog count != visible planners count';
+        END IF;
+        IF (SELECT COALESCE(SUM(view_count),0) + COALESCE(SUM(upvotes),0) FROM planners) <>
+           (SELECT COALESCE(SUM(s.view_count),0) + COALESCE(SUM(s.upvotes),0)
+            FROM planner_stats s JOIN planners p ON p.id = s.planner_id) THEN
+            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'decomposition parity: stats counter sums != planners counter sums';
+        END IF;
+    END IF;
+END;
```

Row-count parity on every aggregate table, catalog-membership-equals-visibility, and counter-sum parity all gate the drop. Only when they hold does `DROP TABLE planners` (and the replaced `planner_content_index`) run, after which the core FK is renamed to its canonical `fk_planner_user`.

### 3. The aggregate — Planner.java + PlannerContent.java

`Planner.java` is rewritten from a wide entity into a thin root. The `@Table` loses its three owner-write indexes, the class implements `Persistable<UUID>`, and every mutable column is deleted from it:

```diff
-@Table(name = "planners",
-       indexes = { ... @Index(name = "idx_user_modified", ...) ... })
+@Table(name = "planner")
 @Getter @Builder @NoArgsConstructor
-@AllArgsConstructor
-public class Planner {
+@AllArgsConstructor(access = AccessLevel.PRIVATE)
+public class Planner implements Persistable<UUID> {
```

```diff
-    @Column(nullable = false) @Setter @Builder.Default
-    private String title = "Untitled";
-    ... (title, category, status, content, versions, keywords, view_count,
-        moderation columns, publishing columns, @Version all removed) ...
+    @OneToOne(mappedBy = "planner", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
+    private PlannerContent content;
+    @OneToOne(mappedBy = "planner", ...) private PlannerPublication publication;
+    @OneToOne(mappedBy = "planner", ...) private PlannerModeration moderation;
```

`Persistable.isNew()` exists so a client-assigned UUID still takes the *persist* path (batchable INSERTs) instead of Hibernate's merge SELECT-then-INSERT — `@PostLoad`/`@PostPersist` flip `isNew` to false. The root keeps its behavior methods but they now delegate to the satellites, and it exposes a facade of `getTitle()`/`getStatus()`/`getPublished()`/… readers that forward into `content`/`publication`/`moderation` so existing callers keep compiling. The takedown guard lives in `togglePublished`, now reading and writing across two satellites:

```diff
 public boolean togglePublished() {
-        if (isTakenDown() && !published) {
+        if (moderation.isTakenDown() && !publication.getPublished()) {
             throw new PlannerForbiddenException(id);
         }
-        boolean nowPublished = !published;
-        this.published = nowPublished;
-        if (nowPublished && firstPublishedAt == null) {
-            this.firstPublishedAt = Instant.now();
-        }
-        return nowPublished;
+        return publication.toggle();
 }
```

`takeDown()` likewise fans out (`moderation.takeDown(); publication.unpublish();`). The point is that moderation state now lives on its own row that the owner's save path never touches — which is why the old "preserve takedown across sync" dance disappears from the write path (see §4).

`PlannerContent.java` is the new owner-hot row, and its class comment states the invariant directly: "no secondary index, no inbound FK (INV6), so a concurrent same-row write can only serialize on the row's X lock — never deadlock via a child's shared lock." Its optimistic-lock column is the aggregate's single lock boundary:

```diff
+    @Version
+    @Column(name = "row_lock_version", nullable = false)
+    private Long rowLockVersion;
```

The `@MapsId` `@OneToOne` back to `Planner` makes the content PK equal the core PK. Note two type upgrades carried in the move: `device_id` becomes a real `UUID`/`BINARY(16)` and `selected_keywords` becomes JSON.

### 4. The write path

`PlannerCommandService` swaps its `PlannerIndexService` dependency for `PlannerCatalogService` and adds `PlannerStatsRepository`. Creation now builds the whole aggregate and seeds an empty stats row in one place:

```diff
+    Planner saved = plannerRepository.save(buildAggregate(UUID.fromString(req.id()), user, req, deviceId));
+    statsRepository.save(PlannerStats.builder().plannerId(saved.getId()).build());
```

`applyRequestFields` now returns a boolean — whether the *searchable composition* (content or keywords) changed — so the caller knows whether the filter indexes need rebuilding, and it normalizes keywords at the domain boundary (`PlannerKeywords.fromClient(...)`) so column, index, and facets all carry current ids. In `upsertPlanner`, the query switches to the aggregate loader and the takedown-preservation block is *deleted* because takedown no longer lives on a row the owner write can clobber:

```diff
-    var existingPlanner = plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);
+    var existingPlanner = plannerRepository.findAggregateForOwner(id, userId);
 ...
-            // Preserve moderator takedown status (allow sync but keep taken-down)
-            Instant originalTakenDownAt = planner.getTakenDownAt();
-
             if (!force && req.syncVersion() != null && !planner.getSyncVersion().equals(req.syncVersion())) {
                 throw new PlannerConflictException(req.syncVersion(), planner.getSyncVersion());
             }
-            applyRequestFields(planner, ..., true);
-            if (req.contentVersion() != null) { planner.setContentVersion(req.contentVersion()); }
-            // Restore moderator takedown status (preserve across syncs)
-            if (originalTakenDownAt != null) { planner.setTakenDownAt(originalTakenDownAt); }
-            planner.setSchemaVersion(currentSchemaVersion);
+            boolean compositionChanged = applyRequestFields(planner, ..., true);
+            if (req.contentVersion() != null) { planner.getContent().setGameContentVersion(req.contentVersion()); }
+            planner.getContent().setContentSchemaVersion(currentSchemaVersion);
             planner.recordSave();
 ...
             if (Boolean.TRUE.equals(saved.getPublished())) {
-                plannerIndexService.reindex(saved.getId(), saved.getContent());
+                plannerCatalogService.onVisibleEditCommitted(saved, compositionChanged);
             }
```

`PlannerCatalogService` is new and is the single choke point for the catalog projection. It exposes *transition intents* rather than raw mutations, each pairing the catalog row with the filter indexes:

```diff
+    @Transactional
+    public void onBecameVisible(Planner planner) {
+        add(planner);
+        filterService.requestRebuild(planner.getId(), planner.getContentJson(),
+                planner.getSelectedKeywords());
+    }
+    @Transactional
+    public void onBecameInvisible(UUID plannerId) {
+        remove(plannerId);
+        filterService.requestClear(plannerId);
+    }
+    @Transactional
+    public void onVisibleEditCommitted(Planner planner, boolean compositionChanged) {
+        syncScalarCopy(planner);
+        if (compositionChanged) {
+            filterService.requestRebuild(planner.getId(), planner.getContentJson(),
+                    planner.getSelectedKeywords());
+        }
+    }
```

`syncScalarCopy` keeps title/category/keywords read-your-writes-fresh in the catalog synchronously, while the heavier inverted-index work is deferred. `add` computes the derived `recommended` flag from current stats + moderation state, so a republished planner keeps its votes.

`PlannerFilterService` is where the deferral happens. Writers only *request* maintenance; the multi-statement index rebuild runs in its own transaction **after** the owning transaction commits, keeping the cross-region write path short:

```diff
+    public void requestRebuild(UUID plannerId, String contentJson, Set<String> selectedKeywords) {
+        eventPublisher.publishEvent(PlannerFilterRebuildEvent.rebuild(plannerId, contentJson, selectedKeywords));
+    }
+
+    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void onFilterRebuildRequested(PlannerFilterRebuildEvent event) {
+        rebuildFilters(event.plannerId(), event.contentJson(), event.selectedKeywords());
+    }
```

`rebuildFilters` deletes both filter tables for the planner, re-extracts entities from the content JSON via `PlannerContentEntityExtractor`, and `saveAll`s the fresh rows.

### 5. The read path

`PublishedPlannerQueryService` is re-pointed from the `planners` table to the catalog projection. It drops `PlannerCommentRepository`, `StatsReadsFlag`, and the `recommendedThreshold` value (recommendation is now a stored flag on the catalog row), and gains `PlannerCatalogRepository`. Listing queries read catalog rows ordered by recency:

```diff
-        Page<Planner> planners;
+        Page<PlannerCatalog> rows;
         boolean hasSearch = search != null && !search.isBlank();
         if (hasSearch) {
-            planners = plannerRepository.findPublishedWithSearch(search.trim(), pageable);
+            Specification<PlannerCatalog> spec = CatalogSpecifications.matchesQuery(search.trim());
+            if (category != null) spec = spec.and(CatalogSpecifications.hasCategory(category));
+            rows = catalogRepository.findAll(spec, recencySorted(pageable));
         } else if (category == null) {
-            planners = plannerRepository.findByPublishedTrueAndDeletedAtIsNullAndTakenDownAtIsNull(pageable);
+            rows = catalogRepository.findAllByOrderByFirstPublishedAtDesc(unsorted(pageable));
         } ...
-        return mapPlannersWithUserContext(planners, userId);
+        return mapCatalogWithUserContext(rows, userId);
```

`incrementViewCount` no longer updates a `planners` column — it checks existence on the core and increments `planner_stats`. Faceted search drops the whole `Specification<Planner>` param in favor of a `boolean recommendedOnly`, and entity facets become EXISTS subqueries over the inverted index (with `parseEntityId` mapping an unparseable id to `-1` so it matches nothing). The list assembler shows the CQRS join done in application code — catalog page ⨝ stats ⨝ core-info, each a single batch query keyed on the page's ids:

```diff
+    private Page<PublicPlannerResponse> mapCatalogWithUserContext(Page<PlannerCatalog> rows, Long userId) {
+        List<UUID> plannerIds = rows.getContent().stream()
+                .map(PlannerCatalog::getPlannerId).collect(Collectors.toList());
+        Map<UUID, PlannerCoreInfo> coreInfoMap = plannerIds.isEmpty() ? Map.of()
+                : plannerRepository.findCoreInfoByIds(plannerIds).stream()
+                        .collect(Collectors.toMap(PlannerCoreInfo::plannerId, Function.identity()));
+        Map<UUID, PlannerStats> statsMap = plannerIds.isEmpty() ? Map.of()
+                : plannerStatsRepository.findAllById(plannerIds).stream()
+                        .collect(Collectors.toMap(PlannerStats::getPlannerId, Function.identity()));
+        ...
```

`CatalogSpecifications` replaces `PlannerSpecifications`. Because catalog membership *is* visibility, no predicate checks published/deleted/taken-down anymore. `matchesQuery` is the notable one — it ORs ngram FULLTEXT relevance on the title against exact membership in the keyword index:

```diff
+    public static Specification<PlannerCatalog> matchesQuery(String q) {
+        String booleanQuery = toBooleanQuery(q);
+        return (root, query, cb) -> cb.or(
+                cb.greaterThan(
+                        cb.function("match_against", Double.class,
+                                root.get("title"), cb.literal(booleanQuery)),
+                        0.0),
+                keywordExists(root, query, cb, q.trim())
+        );
+    }
```

```diff
+    static String toBooleanQuery(String raw) {
+        return Arrays.stream(raw.trim().split("\\s+"))
+                .filter(t -> !t.isEmpty())
+                .map(t -> '"' + t.replace("\"", "") + '"')
+                .collect(Collectors.joining(" "));
+    }
```

Quoting each term as a phrase neutralizes boolean-mode operators in user input. The `match_against` function is registered by the new `MySqlFunctionContributor`, which maps HQL/Criteria `match_against(col, q)` to MySQL's `MATCH (col) AGAINST (q IN BOOLEAN MODE)` and is wired in via a `META-INF/services/org.hibernate.boot.model.FunctionContributor` service file:

```diff
+        functionContributions.getFunctionRegistry()
+                .patternDescriptorBuilder("match_against", "MATCH (?1) AGAINST (?2 IN BOOLEAN MODE)")
+                .setInvariantType(... StandardBasicTypes.DOUBLE ...)
+                .setExactArgumentCount(2)
+                .register();
```

### 6. Deletions that are the story

Two test changes assert the deadlock is *gone*, not merely tolerated. `PlannerUpsertConflictIT` deletes its 503-retry escape hatch and asserts the opposite:

```diff
-                // InnoDB can resolve the same-row collision as a deadlock (503 DEADLOCK)
-                // instead of letting the loser reach the @Version check (409). ... race again.
-                if (loser.getStatus() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
-                    continue;
-                }
+                assertThat(loser.getStatus())
+                        .as("a same-row race must lose at the optimistic-lock check (409), never as a 503 deadlock")
+                        .isNotEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
```

`MySQLIntegrationTest` removes `castVoteWithDeadlockRetry` entirely and calls `castVote` directly, because votes now write only `planner_stats` (atomic upsert) with the vote-row FK targeting the write-once core:

```diff
-                        castVoteWithDeadlockRetry(voter.getId(), testPlanner.getId());
+                        // No deadlock retry: every voter writes only planner_stats
+                        // (atomic upsert) and the vote row's FK targets the
+                        // write-once planner core, so no lock upgrade can cycle
+                        plannerEngagementService.castVote(voter.getId(), testPlanner.getId(), VoteType.UP);
```

```diff
-        // Concurrent votes deadlock on the planner_votes FK + upvotes update (InnoDB
-        // lock upgrade); production maps this to a retryable error, so retry like a client.
-        private void castVoteWithDeadlockRetry(Long userId, UUID plannerId) {
-            for (int attempt = 1; ; attempt++) {
-                try { plannerEngagementService.castVote(userId, plannerId, VoteType.UP); return; }
-                catch (CannotAcquireLockException deadlock) { ... LockSupport.parkNanos(...); }
-            }
-        }
```

Three production classes are deleted outright — they were the machinery the new shape makes obsolete: `PlannerIndexService.java` (JSON extraction into the old `planner_content_index`, replaced by `PlannerFilterService`), `PlannerSpecifications.java` (predicates over `Planner`, replaced by `CatalogSpecifications`), and `StatsReadsFlag.java` (the dual-write cutover flag that gated reading counters from `planner_stats` vs the legacy column — obsolete once `planners` is dropped).

### 7. The invariant, pinned

`PlannerSchemaDecompositionIT` is a schema drill that asserts the shape directly. The INV6 test is the one that pins the deadlock fix — it verifies `planner_content` carries *only* its primary key and that nothing FK-references it:

```diff
+    @Test
+    @DisplayName("inv6_WhenPlannerContent_NoSecondaryIndexNoInboundFk")
+    void inv6_WhenPlannerContent_NoSecondaryIndexNoInboundFk() {
+        assertThat(indexes("planner_content"))
+                .as("planner_content carries only its PRIMARY KEY (INV6: no secondary index)")
+                .containsExactly("PRIMARY");
+        Integer inboundFks = jdbc.queryForObject(
+                "SELECT COUNT(*) FROM information_schema.key_column_usage "
+                        + "WHERE table_schema = DATABASE() AND referenced_table_name = 'planner_content'",
+                Integer.class);
+        assertThat(inboundFks)
+                .as("no table FK-references planner_content (INV6: no inbound FK)")
+                .isZero();
+    }
```

A companion `schemaDrill_*` test checks all eight tables exist with their expected columns/indexes and that `planner_content` no longer carries `saved_at` — the property the whole change exists to guarantee, now enforced against the live migrated schema rather than trusted.
