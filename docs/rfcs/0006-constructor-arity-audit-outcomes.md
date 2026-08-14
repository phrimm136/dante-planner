---
status: Draft
tracking: none
---

# 0006 Constructor-arity audit outcomes

## Summary

A backend-wide constructor-arity audit resolved into five bodies of work: full removal of the
never-active bookmark feature, a restructuring of the token refresh path culminating in one
consolidated token-lifecycle service, completion of the notification feature (per-type factories
and the unwired `REPORT_RECEIVED` type), a server-side restriction gate on edits that reach
published content, and an ArchUnit form ratchet on configuration scalars in constructors. The
audit's remaining findings closed as recorded accept or reject rulings rather than work.

## Motivation

Twenty-three classes carry five or more constructor arguments. The count itself turned out to be a
thermometer: behind the worst offenders sat a feature whose write path was deleted while its
storage and read plumbing lived on, a rotation rollout completed months ago whose legacy branch
was never removed, a notification type declared but never produced, a moderation rule enforced
only in the bypassable client layer, and configuration keys bound independently in up to three
places. Each is a seam that desynchronized silently because no gate can see absence. After this
lands, the dead halves are gone, the one real security gap is closed server-side, and two new
gates (a `NotificationType` matrix test and a constructor-`@Value` ban) convert the recurring
desync class from silent to loud.

## Current behavior

- The bookmark write path was deleted by RFC 0003 Stream 1 item 16; the read side survives:
  `PlannerBookmarkRepository` serves `isBookmarked` on three GET endpoints, the
  `planner_bookmarks` table (created in `V006`, repointed in `V052`) has no writer,
  `KnownConstraint.PLANNER_BOOKMARK` maps violations nothing can produce, and four i18n stubs plus
  stale docs still advertise the toggle. `docs/debt.md` records the table-and-mapping drop as an
  undecided tail.
- `JwtAuthenticationFilter` holds ten collaborators. Its non-lineage rotation branch is guarded by
  `LineageRotationFlag` (`jwt.rotation.lineage-enabled`, default `false`) although the lineage
  rollout completed around 2026-05; `RefreshRotationService` still admits legacy-format tokens via
  `legacyAdmitEnabled` and synthesized family ids.
- `TokenBlacklistService` and `RefreshRotationService` are coupled bidirectionally: the logout Lua
  in the blacklist writes rotation's `REVOKED_FIELD` into `rt:fam:*` hashes, and rotation's Lua
  reads the blacklist's `uinv:` keys. Blacklist reads fail open on a replica template; rotation
  fails closed on the primary.
- `Notification` carries a 3-arg constructor with no production caller and a 7-arg constructor
  with adjacent same-typed parameters; `NotificationType.REPORT_RECEIVED` has no producer, no
  `DomainEventType` counterpart, and no effect arm; `PLANNER_PUBLISHED` rows are written by a
  native fanout insert that bypasses entity construction.
- `PlannerCommandService.upsertAggregate` propagates edits of a published planner to the public
  catalog via `PlannerCatalogService.onVisibleEditCommitted` with no restriction check, while
  every other public-contribution write calls `PlannerAccessGuard`. The frontend disables the sync
  button for restricted users, so enforcement exists only client-side. `createPlanner` and
  `updatePlanner` have no production callers.
- Twenty-nine constructor parameters are `@Value`-bound in feature classes;
  `planner.recommended-threshold` binds in three classes, four other keys bind twice, and
  `JwtProperties` owns the `jwt` prefix while two `jwt.*` keys are read beside it via `@Value`.

## Prior art

- ADR 072 governs all observer effects; the new `REPORT_RECEIVED` wiring lands inside its outbox
  discipline and ArchUnit rules.
- `SseEventTypeMatrixTest` is the in-repo template for the enum-coverage matrix test.
- ADR 063 (ratchets carve out named testable forms, not class lists) and ADR 028 (a gate flips
  blocking only at a zero baseline) shape the constructor-`@Value` ratchet.
- ADR 058 fixes restriction-before-validation response ordering, which places the new upsert gate.
- RFC 0003 item 16 is the decision the bookmark removal overturns; its own rationale ("it
  renders") is contradicted by the field having only ever rendered `false`.

## Proposal

Remove the bookmark feature entirely — read side, storage, wire field, translations, and stale
documentation — in one coordinated release. Restructure the refresh path: delete the legacy
non-lineage rotation branch and legacy-token admission, extract the filter's refresh orchestration
into a `SessionRefresher` collaborator, consolidate `jwt.rotation.*` binding under the existing
`jwt` properties class, and merge the blacklist and rotation services into one
`TokenLifecycleService` owning all token revocation and lineage state, with the fail-open posture
scoped to revocation reads and the fail-closed posture to lineage writes. Complete the
notification feature: named static factories per notification type, deletion of the dead
constructor and of nothing else, and production of `REPORT_RECEIVED` from both report services to
admin recipients through the outbox. Close the restriction gap: an upsert that would mutate a
published planner rejects restricted users before validation. Guard the config surface with an
ArchUnit rule banning `@Value` constructor parameters outside `@ConfigurationProperties` classes.

## Decomposition

```
- bookmark-removal — the bookmark feature, its table, wire field, i18n stubs, and doc references are gone
- legacy-lineage-removal — the non-lineage rotation branch, legacy admission, synthesized family ids, and the rollout flag are gone
- session-refresher — the filter delegates refresh orchestration to one collaborator; behavior unchanged (after: legacy-lineage-removal)
- rotation-properties — jwt.rotation.* binds once, nested under the existing jwt properties class (after: legacy-lineage-removal)
- token-lifecycle-consolidation — one TokenLifecycleService owns access hashes, user invalidation, families, and both Lua scripts; logout drops the refresh token revocation (after: legacy-lineage-removal)
- notification-factories — named per-type factories; the 3-arg constructor is gone
- report-received-wiring — REPORT_RECEIVED flows from both report services to admin inboxes through the outbox; the NotificationType matrix test exists (after: notification-factories)
- upsert-restriction-gate — restricted users cannot mutate published planners; the FE disable narrows to published planners
- config-consolidation — every duplicated property key binds in exactly one place; feature config moves to @ConfigurationProperties
- dead-surface-removal — dead service methods, the unused controller dependency, stale doc rows, and the unreachable constraint mapping are gone
- value-param-ratchet — the ArchUnit ban on @Value constructor params is blocking at a zero baseline (after: rotation-properties, config-consolidation)
```

## Scenarios

```gherkin
Scenario: List responses carry no bookmark field (bookmark-removal)
  Given an authenticated user requests the published planner list
  When the response returns
  Then the status is 200 and no object in the payload contains an isBookmarked key, and the frontend schema parses it

Scenario: The bookmark table is gone (bookmark-removal)
  Given the latest migration has run
  When the schema is inspected
  Then planner_bookmarks does not exist and no KnownConstraint entry maps it

Scenario: No bookmark identifier survives outside history (bookmark-removal)
  Given the change is complete
  When backend main sources, frontend sources, and static i18n files are swept case-insensitively for "bookmark"
  Then the sweep returns zero hits (RFC and ADR history exempt)

Scenario: A legacy-format refresh token is rejected (legacy-lineage-removal)
  Given a refresh token carrying no familyId claim
  When it is presented for refresh
  Then the session is abandoned, auth cookies are cleared, and the request continues as guest

Scenario: Refresh still works through the extraction (session-refresher)
  Given a user with an expired access cookie and a valid lineage refresh token
  When they make an authenticated request
  Then the response is 200 with a new access cookie and a rotated refresh cookie

Scenario: Redis outage during refresh still degrades (session-refresher)
  Given Redis is unreachable
  When a refresh is attempted
  Then the response is 503 with code AUTH_UNAVAILABLE and the filter chain does not proceed

Scenario: Rotation reads its window from the properties class (rotation-properties)
  Given jwt.rotation.retry-reuse-window-ms is set to 30000
  When the application boots
  Then the rotation service observes 30000 and no @Value binding for a jwt.rotation key exists

Scenario: Logout kills the family and the access token atomically (token-lifecycle-consolidation)
  Given a logged-in session with a lineage refresh token
  When the user logs out and the old refresh token is later replayed
  Then rotation of that family fails as revoked, the old access token is rejected with 401, and the logout call recorded exactly one access-token revocation and one family revocation

Scenario: A comment notification is built by its factory (notification-factories)
  Given a comment lands on a published planner
  When the notification row is created
  Then its type is COMMENT_RECEIVED and its comment fields are populated, and no caller constructs Notification through a public multi-argument constructor

Scenario: A planner report notifies admins (report-received-wiring)
  Given at least one admin user exists
  When a user reports a published planner
  Then the reporting transaction commits exactly one domain_events row of type REPORT_RECEIVED, and the dispatcher derives one notification per admin with type REPORT_RECEIVED

Scenario: Duplicate reports collapse (report-received-wiring)
  Given an admin already has a REPORT_RECEIVED notification for a piece of content
  When a second report of the same content is dispatched
  Then the insert is ignored on (user_id, content_id, notification_type) and no second notification row exists

Scenario: A crash before dispatch is redelivered (report-received-wiring)
  Given a committed REPORT_RECEIVED domain_events row whose dispatched_at is null past the grace window
  When the relay runs
  Then the notification rows exist and dispatched_at is set

Scenario: A restricted user cannot edit published content (upsert-restriction-gate)
  Given a banned or timed-out user owning a published planner
  When they upsert that planner
  Then the response is 403 (UserBannedException or UserTimedOutException) and the catalog receives no visible-edit call

Scenario: A restricted user keeps private planner work (upsert-restriction-gate)
  Given a banned or timed-out user owning an unpublished planner
  When they upsert that planner
  Then the write succeeds exactly as for an unrestricted user

Scenario: A new @Value constructor parameter fails the build (value-param-ratchet)
  Given a class outside @ConfigurationProperties gains an @Value-annotated constructor parameter
  When the architecture tests run
  Then the @Value ratchet rule fails

Scenario: Duplicated keys bind once (config-consolidation)
  Given the application boots
  When property bindings are inspected
  Then planner.recommended-threshold, planner.schema-version, planner.md.current-version, planner.rr.available-versions, cors.allowed-origins, and app.user.deletion.grace-period-days each have exactly one binding site

Scenario: The dead command surface is gone (dead-surface-removal)
  Given the change is complete
  When the planner command service is inspected
  Then createPlanner and updatePlanner do not exist, upsert, delete, and import remain, and exactly one constructor remains
```

## Invariants

- Every observer effect derives only from a committed `domain_events` row — existing ArchUnit
  rules; `report-received-wiring` must land inside them.
- Every `NotificationType` value has a producer, a `DomainEventType` counterpart, and an effect
  arm — gate: the `NotificationType` matrix test added by `report-received-wiring`.
- Restriction checks run before validation on every write they guard, so 403 wins over 400/409 —
  gate: the upsert-gate unit tests.
- Revocation reads fail open and lineage writes fail closed, and consolidation into one class does
  not flatten the two postures — gate: the existing degradation integration tests must pass
  unmodified.
- The refresh flow's observable behavior is unchanged by the refresher extraction — gate: existing
  filter and rotation integration tests pass unmodified.

## Decisions

- @bookmark — Remove the feature entirely, table included, because the write path never had a
  production caller and the read side has only ever rendered false. Overturns RFC 0003 item 16.
  REJECTED: keeping the read-side projection — it renders a constant.
- @bookmark @deploy — Big-bang single release. REJECTED: FE-first two-step ordering — accepted
  consequence instead: browsers holding the old bundle fail list parsing until reload.
- @notification — Named static factories per type. REJECTED: JPA inheritance — the hottest write
  path bypasses entity construction entirely and the read side never dispatches on type.
- @notification @report — `REPORT_RECEIVED` is wired, not deleted: recipients are admin/moderator
  users, producers are both report services, dedup key is (user_id, content_id,
  notification_type) so repeat reports collapse. REJECTED: deleting the enum value — the type was
  intended, not accidental.
- @auth @rotation — The legacy non-lineage path is removed now, because the rollout completed and
  the seven-day refresh TTL bounds any surviving legacy token to zero. REJECTED: continued
  admission of legacy-format tokens.
- @auth @refresh — Refresh orchestration extracts into a `SessionRefresher` collaborator, keeping
  the repo's session vocabulary (a refresh-token family is the session). REJECTED: a second
  servlet filter — the refresh path shares the authentication verdict, response cookies, abandon
  protocol, and outage short-circuit with the filter. REJECTED: token-first naming — the session
  vocabulary is already established by the surrounding types.
- @auth @token — `TokenBlacklistService` and `RefreshRotationService` consolidate into one
  `TokenLifecycleService` (user-ruled, overturning this same debate's earlier two-service
  position). Accepted consequence: one class carries both failure postures, scoped per method
  group. REJECTED: two services with one-way key-schema coupling. REJECTED: the name
  `TokenRevocationService` — rotation mints tokens, which is not revocation.
- @auth @logout — Logout drops the refresh-token hash revocation once legacy admission is gone;
  family revocation covers it and is stronger under outage, because the hash check fails open
  while the family check fails closed. REJECTED: keeping both — a fail-open backup behind a
  fail-closed primary adds storage, not guarantee.
- @config — `jwt.rotation.*` binds once, nested under the existing `jwt` properties class,
  absorbing the standalone rotation flag bean. REJECTED: three parallel binding mechanisms for
  one prefix.
- @convention @archunit — The arity problem gets a form ratchet, not a number: `@Value`
  constructor parameters are banned outside `@ConfigurationProperties` classes, flipping to
  blocking at a zero baseline. REJECTED: a numeric arity cap — a frozen over-cap class growing
  worse adds no new violation, so freezing is blind exactly where it matters. REJECTED: Sonar
  S107 — a second toolchain gate for a rule the existing architecture suite can express.
- @validators — No unified validate-context contract; the verb-shaped validators stand as named,
  order-explicit preconditions, and the command service honestly lands at eleven constructor
  arguments. REJECTED: a uniform `validate(context)` pipeline — it erases per-operation verb
  selection and re-encodes it as dispatch. REJECTED: a bundled holder object — count cosmetics.
  REJECTED: per-command handler split — same verdict as the publishing service's accept.
- @planner @moderation — Restriction gates on the planner's public state, not the endpoint's
  classification: upsert of a published planner rejects restricted users before validation, and
  the frontend's blanket sync disable narrows to published planners. REJECTED: blocking all sync
  for restricted users — withdraws the private work the guard's contract preserves. REJECTED:
  status quo — the only public mutation without a server-side check, enforced solely in the
  bypassable layer.
- @query @publishing — `PublishedPlannerQueryService` is not split and `notificationTargetOf`
  stays; `PlannerPublishingService`, `PlannerAccountPurgeService`, and
  `UserAccountLifecycleService` are accepted as-is. REJECTED: list/detail split — two read
  aggregators sharing three infrastructure deps buy little for a new seam.
- Spike: production `planner_bookmarks` row count — inconclusive (no live AWS session);
  superseded by the user's ruling that the feature was never active and the table holds zero
  rows.

## Drawbacks

- Bookmark resurrection becomes a full rebuild: table, wire field, and UI are all gone.
- The single-table notification shape persists; a future genuinely polymorphic notification type
  reopens the mapping question.
- `TokenLifecycleService` is a large class whose correctness depends on per-method failure
  postures that one careless error-handling unification could flatten.
- The publishing service remains the largest coordinator and a merge-conflict focal point.

## Non-goals

- Delete-path restriction semantics: deleting a published planner auto-unpublishes without a
  restriction check today; same species as the upsert gap, deliberately left for its own ruling.
- Splitting `PublishedPlannerQueryService`, unifying validators, or re-partitioning notification
  storage — argued and closed as rejections above; they must not ride along.
- Matrix ratchets beyond `NotificationType`.
- Negative scenario: no unit of this RFC adds a restriction check to any private-planner write
  path other than the published-upsert branch.

## Risks and rollback

- Big-bang bookmark window: stale bundles fail list parsing until reload; detected in frontend
  error tracking; self-heals on reload; a backend revert restores the field, and at zero rows the
  table is recreatable, so nothing is permanently unundoable.
- Legacy removal: a straggler legacy token (impossible per TTL) forces a re-login; watched via the
  existing rotation metrics.
- Consolidation: a regression in either failure posture is caught by the existing degradation
  integration tests, which must pass unmodified.
- Report fanout: bounded by admin count and the dedup key.

## Open questions

