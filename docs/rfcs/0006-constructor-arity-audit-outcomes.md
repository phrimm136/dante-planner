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

## The rule

> Every seam the audit exposed either loses its dead half, gains its missing
> server-side gate, or gets a ratchet that makes the next silent desync loud; a
> finding meriting none of those closed as a recorded ruling, not code.

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
  `KnownConstraint.PLANNER_BOOKMARK` maps violations nothing can produce,
  `PlannerBookmarkRepository.countByPlannerId` has no caller, and four i18n stubs plus stale docs
  still advertise the toggle. `docs/debt.md` records the table-and-mapping drop as an undecided
  tail. The read side's whole visible life:

  ```tsx
  /** Whether current user has bookmarked; false when not authenticated */
  isBookmarked: z.boolean(),          // required by the read schema; no writer has ever set it true

  {showBookmark && isBookmarked && (  // render-only; no mutation path exists anywhere
    <Bookmark className="size-4 fill-primary text-primary" />
  )}
  ```
- `JwtAuthenticationFilter` holds ten collaborators. Its non-lineage rotation branch is guarded by
  `LineageRotationFlag` (`jwt.rotation.lineage-enabled`, default `false`) although the lineage
  rollout completed around 2026-05; `RefreshRotationService` still admits legacy-format tokens via
  `legacyAdmitEnabled` and synthesized family ids. The fork at proposal time (condensed — this
  method is also the orchestration the refresher extraction moves):

  ```java
  if (lineageRotationFlag.isEnabled()) {
      RotationResult.Rotated rotated = refreshRotationService.rotate(refreshToken).orThrow();
      cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, rotated.newRefreshJwt(), ...);
      return true;
  }
  // legacy branch: mints locally, records no lineage
  tokenBlacklistService.blacklistTokenForRotation(refreshToken, claims.expiration());
  cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN,
          tokenGenerator.generateRefreshToken(user.getId()), ...);
  ```
- `TokenBlacklistService` and `RefreshRotationService` are coupled bidirectionally: the logout Lua
  in the blacklist writes rotation's `REVOKED_FIELD` into `rt:fam:*` hashes, and rotation's Lua
  reads the blacklist's `uinv:` keys. Blacklist reads fail open on a replica template; rotation
  fails closed on the primary. The family hash tracks per-jti states `UNUSED_LATEST`, `PENDING`,
  `RETIRED`, `SUPERSEDED`, plus a legacy `USED` spelling matched in the theft check. The coupling
  at proposal time, verbatim:

  ```java
  // TokenBlacklistService — the logout Lua writes rotation's namespace:
  + "  redis.call('HSET', KEYS[i], '" + RefreshRotationService.REVOKED_FIELD + "', ARGV[tokens + 3])\n"
  ...
  case LogoutRevocation.FamilyRevocation family ->
          familyKeys.add(RefreshRotationService.familyKey(family.familyId()));

  // RefreshRotationService — its rotate Lua reads the blacklist's namespace:
  private String userInvalidationKey(Long userId) {
      return TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + userId;
  }
  ```
- `Notification` carries a 3-arg constructor with no production caller and a 7-arg constructor
  with adjacent same-typed parameters; `NotificationType.REPORT_RECEIVED` has no producer, no
  `DomainEventType` counterpart, and no effect arm; `PLANNER_PUBLISHED` rows are written by a
  native fanout insert that bypasses entity construction. The telescoping surface at proposal
  time:

  ```java
  public Notification(Long userId, String contentId, NotificationType notificationType)  // no production caller
  public Notification(Long userId, String contentId, NotificationType notificationType,
                      UUID plannerId, String plannerTitle, String commentSnippet, UUID commentPublicId)
  public static Notification plannerScoped(Long userId, String contentId, NotificationType type,
                                           UUID plannerId, String plannerTitle)
  ```

  And the unwired declaration, visible as a one-value mismatch between the two enums (condensed):

  ```java
  enum NotificationType { PLANNER_RECOMMENDED, PLANNER_PUBLISHED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED }
  enum DomainEventType  { PLANNER_PUBLISHED, PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED }
  // REPORT_RECEIVED: declared for the inbox, absent from the outbox — no producer can exist
  ```
- `PlannerCommandService.upsertAggregate` propagates edits of a published planner to the public
  catalog via `PlannerCatalogService.onVisibleEditCommitted` with no restriction check, while
  every other public-contribution write calls `PlannerAccessGuard`:

  ```java
  if (planner.isPublished()) {
      plannerCatalogService.onVisibleEditCommitted(planner);  // public side effect on a path that never consults the guard
  }
  ``` The frontend disables the sync
  button for restricted users, so enforcement exists only client-side. `createPlanner`,
  `updatePlanner`, and `PublishedPlannerQueryService.incrementViewCount` have no production
  callers.
- Twenty-nine constructor parameters are `@Value`-bound in feature classes;
  `planner.recommended-threshold` binds in three classes, five other keys bind twice, and
  `JwtProperties` owns the `jwt` prefix while two `jwt.*` keys are read beside it via `@Value`:

  ```java
  // one key, three independent bindings
  PlannerCatalogService:    @Value("${planner.recommended-threshold}") int recommendedThreshold
  PlannerDriftReconciler:   @Value("${planner.recommended-threshold}") int recommendedThreshold
  PlannerEngagementService: @Value("${planner.recommended-threshold}") int recommendedThreshold

  // one prefix, three binding mechanisms
  @Value("${jwt.rotation.legacy-admit-enabled:true}") boolean legacyAdmitEnabled      // ctor param
  @Value("${jwt.rotation.retry-reuse-window-ms:30000}") long retryReuseWindowMs       // ctor param
  public LineageRotationFlag(@Value("${jwt.rotation.lineage-enabled:false}") boolean enabled)  // its own bean
  @ConfigurationProperties(prefix = "jwt")   // JwtProperties — the class that already owns the prefix
  ```

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

Remove the bookmark feature entirely — read side, storage, wire field, icon rendering,
translations, and stale documentation — in one coordinated release; the table drop is a
forward-only migration, and the i18n stubs land as their own commit in the static submodule. Restructure the refresh path: delete the legacy
non-lineage rotation branch and legacy-token admission, extract the filter's refresh orchestration
into a `SessionRefresher` collaborator, consolidate `jwt.rotation.*` binding under the existing
`jwt` properties class, and merge the blacklist and rotation services into one
`TokenLifecycleService` owning all token revocation and lineage state, with the fail-open posture
scoped to revocation reads and the fail-closed posture to lineage writes. Complete the
notification feature: named static factories per notification type (`forComment`, `forReply`,
`forReport`, and `forRecommendation`, the last specializing the existing `plannerScoped` and
dropping its type parameter), deletion of the dead constructor and of nothing else, and
production of `REPORT_RECEIVED` from the planner and comment report services to admin recipients
through the outbox; the native published-fanout insert never constructs the entity, so no factory
applies to it and it stays untouched. Close the restriction gap: an upsert that would mutate a
published planner rejects restricted users before validation. Guard the config surface with an
ArchUnit rule banning `@Value` constructor parameters outside `@ConfigurationProperties` classes.

The bookmark table drop is one migration, one statement:

```sql
DROP TABLE planner_bookmarks;
```

Legacy removal collapses `rotateRefreshCookie` to the lineage arm; the `user` and `claims`
parameters leave with the local-mint branch that needed them:

```java
private boolean rotateRefreshCookie(
        String refreshToken, HttpServletRequest request, HttpServletResponse response) {
    try {
        RotationResult.Rotated rotated = refreshRotationService.rotate(refreshToken).orThrow();
        cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, rotated.newRefreshJwt(),
                jwtProperties.getRefreshTokenExpirySeconds());
        return true;
    } catch (SessionRevokedException e) {
        return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
    } catch (InvalidTokenException e) {
        return abandonSession(request, response, CustomAuthenticationEntryPoint.INVALID_TOKEN);
    }
}
```

The refresher extraction gives the filter one collaborator for the whole refresh path and
shrinks its field block to plumbing:

```java
@Component
public class SessionRefresher {

    /** Owns refresh validation, rotation, access minting, and both cookie writes. */
    public RefreshOutcome attemptRefresh(HttpServletRequest request, HttpServletResponse response) { ... }

    public enum RefreshOutcome { REFRESHED, ABANDONED, OUTAGE_REPORTED }
}

// JwtAuthenticationFilter after extraction:
private final AccessTokenAuthenticator accessTokenAuthenticator;
private final SessionRefresher sessionRefresher;
private final AuthDegradationResponder degradationResponder;
private final CookieUtils cookieUtils;
```

Rotation config nests under the class that owns the prefix; the two legacy keys get no
successor fields because their feature is gone:

```java
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private final Rotation rotation = new Rotation();

    @Getter @Setter
    public static class Rotation {
        @Min(0)
        private long retryReuseWindowMs = 30000L;
    }
}
```

The consolidated service keeps the two failure postures as two method groups:

```java
@Service
public class TokenLifecycleService {
    // fail-open reads (replica template)
    public boolean isBlacklisted(String token) { ... }
    public boolean isUserTokenInvalidated(Long userId, long issuedAtMs) { ... }

    // fail-closed lineage writes (primary template, Lua)
    public RotationResult rotate(String refreshJwt) { ... }
    public void revokeLogoutSession(Collection<LogoutRevocation> revocations) { ... }

    // fail-closed revocation writes (primary template, plain commands)
    public void blacklistToken(String token, Date expiry) { ... }
    public void invalidateUserTokens(Long userId) { ... }
}
```

`logoutAll` re-points to the consolidated service with its semantics unchanged — the user-wide
watermark plus one immediate blacklist of the presented access token; families are deliberately
left untouched because the watermark outranks them:

```java
public void logoutAll(Long userId, String accessToken) {
    tokenLifecycleService.invalidateUserTokens(userId);

    if (accessToken != null) {
        try {
            TokenClaims accessClaims = tokenValidator.validateAccessToken(accessToken);
            tokenLifecycleService.blacklistToken(accessToken, accessClaims.expiration());
        } catch (InvalidTokenException e) {
            log.debug("Access token already invalid, skipping blacklist");
        }
    }
}
```

Its rotate script, with renamed states and named arguments:

```lua
-- KEYS = rt:fam:{F}, uinv:{user}
local familyKey, invalidationKey = KEYS[1], KEYS[2]
local jti, parentJti, succJti                     = ARGV[1], ARGV[2], ARGV[3]
local succExpiryMs, nowMs, familyTtlMs            = ARGV[4], ARGV[5], ARGV[6]
local succJwt, reuseWindowMs, presentedIssuedAtMs = ARGV[7], ARGV[8], ARGV[9]

local invalidatedAtMs = redis.call('GET', invalidationKey)
if invalidatedAtMs and tonumber(presentedIssuedAtMs) < tonumber(invalidatedAtMs) then
  return 'INVALIDATED'
end

if redis.call('HGET', familyKey, '__revoked__') then return 'REVOKED' end

local entry = redis.call('HGET', familyKey, jti)           -- "STATE|succJti|expiryMs" or false
local state = entry and string.match(entry, '^[^|]+') or 'LIVE'

if state == 'RETIRED' or state == 'SUPERSEDED' then
  redis.call('HSET', familyKey, '__revoked__', nowMs)
  return 'THEFT'
end

if parentJti ~= '' then
  local parentEntry = redis.call('HGET', familyKey, parentJti)
  if parentEntry and string.match(parentEntry, '^[^|]+') == 'IN_GRACE' then
    redis.call('HSET', familyKey, parentJti, 'RETIRED||' .. succExpiryMs)
    redis.call('HDEL', familyKey, 'succjwt:' .. parentJti)
  end
end

local outcome = 'ROTATED'
if state == 'IN_GRACE' then
  local priorSuccJti = string.match(entry, '|([^|]*)|')
  local memo = redis.call('HGET', familyKey, 'succjwt:' .. jti)
  if memo and priorSuccJti and priorSuccJti ~= '' then
    local mintedAtMs, memoJwt = string.match(memo, '^(%d+)|(.+)$')
    local priorSuccEntry = redis.call('HGET', familyKey, priorSuccJti)
    if mintedAtMs and priorSuccEntry and string.match(priorSuccEntry, '^[^|]+') == 'LIVE'
        and tonumber(nowMs) - tonumber(mintedAtMs) < tonumber(reuseWindowMs) then
      return 'REUSED|' .. memoJwt
    end
  end
  if priorSuccJti and priorSuccJti ~= '' then
    redis.call('HSET', familyKey, priorSuccJti, 'SUPERSEDED||' .. succExpiryMs)
  end
  outcome = 'SUPERSEDED'
end

redis.call('HSET', familyKey, succJti, 'LIVE||' .. succExpiryMs)
redis.call('HSET', familyKey, jti, 'IN_GRACE|' .. succJti .. '|' .. succExpiryMs)
redis.call('HSET', familyKey, 'succjwt:' .. jti, nowMs .. '|' .. succJwt)
redis.call('PEXPIRE', familyKey, familyTtlMs)
return outcome
```

The logout script keeps its segmented-KEYS form with the same named-locals treatment; its
semantics are unchanged and only the call population shrinks (at most one token, one family):

```lua
-- KEYS = bl:{sha256(token)} x tokenCount, then rt:fam:{familyId} for the rest
-- ARGV = marker, tokenCount, tokenTtlMs..., revokedAtMs, familyTtlMs
local marker, tokenCount = ARGV[1], tonumber(ARGV[2])
local revokedAtMs, familyTtlMs = ARGV[tokenCount + 3], ARGV[tokenCount + 4]

for i = 1, tokenCount do
  redis.call('SET', KEYS[i], marker, 'PX', ARGV[2 + i])
end
for i = tokenCount + 1, #KEYS do
  redis.call('HSET', KEYS[i], '__revoked__', revokedAtMs)
  redis.call('PEXPIRE', KEYS[i], familyTtlMs)
end
return 'OK'
```

The boot-time converter, deleted in the release after it runs:

```java
private static final Map<String, String> STATE_RENAMES = Map.of(
        "UNUSED_LATEST", "LIVE",
        "PENDING", "IN_GRACE",
        "USED", "RETIRED");

// SCAN rt:fam:* ; rewrite only the state prefix of jti fields; HSET preserves key TTLs
for (var entry : hash.entrySet()) {
    String field = entry.getKey();
    if (field.equals("__revoked__") || field.startsWith("succjwt:")) continue;
    String state = entry.getValue().substring(0, entry.getValue().indexOf('|'));
    String renamed = STATE_RENAMES.get(state);
    if (renamed != null) {
        redis.opsForHash().put(key, field, renamed + entry.getValue().substring(state.length()));
    }
}
```

The upsert gate is one guarded call ahead of the sync-version check, so 403 wins over 409:

```java
if (existingPlanner.isPresent()) {
    Planner planner = existingPlanner.get();

    if (planner.isPublished()) {
        accessGuard.checkNotRestricted(userId);
    }
    syncVersionValidator.requireSyncVersionMatch(force, request.syncVersion(), planner.getSyncVersion());
```

The notification factories replace the public constructors:

```java
public static Notification forComment(Long recipientId, UUID commentId, UUID plannerId,
        String plannerTitle, String commentSnippet, UUID commentPublicId) { ... }
public static Notification forReply(Long recipientId, UUID replyId, UUID plannerId,
        String plannerTitle, String replySnippet, UUID replyPublicId) { ... }
public static Notification forReport(Long adminId, String reportedContentId) { ... }
public static Notification forRecommendation(Long ownerId, UUID plannerId, String plannerTitle) { ... }
```

`REPORT_RECEIVED` wiring follows the existing effect shape end to end:

```java
public enum DomainEventType {
    PLANNER_PUBLISHED, PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED
}

// PlannerReportService and CommentReportService, inside the creating transaction:
domainEventRecorder.recordDomainEvent(DomainEventType.REPORT_RECEIVED, report.getId(),
        Map.of("subjectType", subjectType, "subjectId", subjectId));

// effect arm, the CommentReceivedEffect shape:
@Component
public class ReportReceivedEffect implements DomainEffect {
    public DomainEventType type() { return DomainEventType.REPORT_RECEIVED; }
    public void apply(DomainEvent event) {
        userService.findAdminIds().forEach(adminId -> notificationDispatchService
                .raise(Notification.forReport(adminId, event.getAggregateId())));
    }
}
```

Feature config gets its first `@ConfigurationProperties` class, absorbing every duplicated key:

```java
@ConfigurationProperties(prefix = "planner")
@Validated @Getter @Setter
public class PlannerProperties {
    private int recommendedThreshold;
    private int schemaVersion;
    private int maxPerUser;
}
```

And the ratchet is one architecture rule:

```java
@ArchTest
static final ArchRule noValueConstructorParams = constructors()
        .that().areDeclaredInClassesThat().areNotAnnotatedWith(ConfigurationProperties.class)
        .should(haveNoParameterAnnotatedWith(Value.class));
```

Dead-surface removal is pure deletion and carries no constructive code.

## Plan

Three phases. Jobs within a phase run in parallel and are file-disjoint; each job's
verification is its scenario group below, and its files are stated in the Proposal's
per-topic sections — the Plan keeps no second copy of them.

- **Phase 1**
  - `bookmark-removal` — the bookmark feature, its table (forward-only drop), wire
    field, icon rendering, unreachable constraint mapping, i18n stubs (separate
    static-submodule commit), and doc references are gone, and the open debt entries
    recording them are closed.
  - `legacy-lineage-removal` — the non-lineage rotation branch, legacy admission,
    synthesized family ids and their call sites, and the rollout flag are gone.
  - `notification-factories` — named per-type factories; the 3-arg constructor is gone.
  - `upsert-restriction-gate` — restricted users cannot mutate published planners; the
    FE disable narrows to published planners.
  - `config-consolidation` — every duplicated property key binds in exactly one place;
    feature config moves to @ConfigurationProperties.
  - `dead-surface-removal` — the dead command methods and duplicate constructor, the
    unused controller token validator, the dead view-count increment, and the stale
    RFC index status row are gone.
- **Phase 2**
  - `session-refresher` — the filter delegates refresh orchestration to one
    collaborator; behavior unchanged.
  - `rotation-properties` — jwt.rotation.* binds once, nested under the existing jwt
    properties class.
  - `token-lifecycle-consolidation` — one TokenLifecycleService owns access hashes,
    user invalidation, families, and both Lua scripts; logout drops the refresh token
    revocation; the family state machine is renamed and pre-rename values are
    converted at boot.
  - `report-received-wiring` — REPORT_RECEIVED flows from both report services to
    admin inboxes through the outbox; the NotificationType matrix test exists.
- **Phase 3**
  - `value-param-ratchet` — the ArchUnit ban on @Value constructor params is blocking
    at a zero baseline.

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
  When backend sources, frontend sources including tests, e2e sources, and static i18n files are swept case-insensitively for "bookmark"
  Then the sweep returns zero hits (RFC and ADR history exempt)

Scenario: A legacy-format refresh token is rejected (legacy-lineage-removal)
  Given a refresh token carrying no familyId claim
  When it is presented for refresh
  Then both auth cookies are cleared, the security context stays empty, and the request proceeds unauthenticated

Scenario: Refresh still works through the extraction (session-refresher)
  Given lineage tokens are the only admitted refresh format and a user holds an expired access cookie with a valid refresh token
  When they make an authenticated request
  Then the response is 200 with a new access cookie and a rotated refresh cookie, and the refresher contains no non-lineage branch

Scenario: Redis outage during refresh still degrades (session-refresher)
  Given Redis is unreachable
  When a refresh is attempted
  Then the response is 503 with code AUTH_UNAVAILABLE and the filter chain does not proceed

Scenario: Rotation reads its window from the properties class (rotation-properties)
  Given jwt.rotation.retry-reuse-window-ms is set to 30000
  When the application boots
  Then the rotation service observes 30000, no @Value binding for a jwt.rotation key exists, and no binding for jwt.rotation.lineage-enabled exists at all

Scenario: Logout kills the family and the access token atomically (token-lifecycle-consolidation)
  Given a logged-in session with a lineage refresh token
  When the user logs out and the old refresh token is later replayed
  Then rotation of that family fails as revoked, the old access token is rejected with 401, and the logout call recorded exactly one access-token revocation and one family revocation

Scenario: Pre-rename family state survives the rename (token-lifecycle-consolidation)
  Given family hashes written before the release hold UNUSED_LATEST, PENDING, or USED state prefixes
  When the release boots and the converter completes before readiness
  Then every jti field's state prefix is one of LIVE, IN_GRACE, RETIRED, SUPERSEDED, and no family key's TTL changed

Scenario: A comment notification is built by its factory (notification-factories)
  Given a comment lands on a published planner
  When the notification row is created
  Then its type is COMMENT_RECEIVED, its comment snippet and comment public id are populated, the 3-arg constructor no longer exists, and dispatch call sites construct only through the named factories

Scenario: A planner report notifies admins (report-received-wiring)
  Given exactly two admin users exist
  When a user reports a published planner
  Then the reporting transaction commits exactly one domain_events row of type REPORT_RECEIVED, and the dispatcher derives exactly two notification rows of type REPORT_RECEIVED, one per admin, each built through the report factory

Scenario: A comment report notifies admins (report-received-wiring)
  Given exactly one admin user exists
  When a user reports a comment
  Then the reporting transaction commits exactly one domain_events row of type REPORT_RECEIVED and the admin receives exactly one notification row of type REPORT_RECEIVED

Scenario: Duplicate reports collapse (report-received-wiring)
  Given an admin already has a REPORT_RECEIVED notification for a piece of content
  When a second report of the same content is dispatched
  Then the insert is ignored on (user_id, content_id, notification_type) and no second notification row exists

Scenario: A crash before dispatch is redelivered (report-received-wiring)
  Given a committed REPORT_RECEIVED domain_events row whose dispatched_at is null past the grace window
  When the relay runs
  Then exactly one notification row per admin exists and the domain_events row's dispatched_at is non-null

Scenario: A restricted user cannot edit published content (upsert-restriction-gate)
  Given a banned or timed-out user owning a published planner
  When they upsert that planner
  Then the response is 403 (UserBannedException or UserTimedOutException), no planner field is modified, and the catalog receives no visible-edit call

Scenario: A restricted user keeps private planner work (upsert-restriction-gate)
  Given a banned or timed-out user owning an unpublished planner
  When they upsert that planner
  Then the response is 200 and the planner's syncVersion has advanced by 1

Scenario: A new @Value constructor parameter fails the build (value-param-ratchet)
  Given a class outside @ConfigurationProperties gains an @Value-annotated constructor parameter
  When the architecture tests run
  Then the rule banning @Value constructor parameters outside @ConfigurationProperties fails the build

Scenario: The ratchet flips blocking at a zero baseline (value-param-ratchet)
  Given rotation-properties and config-consolidation have landed
  When the architecture suite runs on current sources
  Then the @Value constructor-parameter rule reports zero violations and runs as a blocking rule

Scenario: Duplicated keys bind once (config-consolidation)
  Given the application boots
  When property bindings are inspected
  Then planner.recommended-threshold, planner.schema-version, planner.md.current-version, planner.rr.available-versions, cors.allowed-origins, and app.user.deletion.grace-period-days each have exactly one binding site, and each surviving binding goes through a @ConfigurationProperties class

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

## Verified facts

1. Production `planner_bookmarks` row count — spike inconclusive (no live AWS
   session); superseded by the user's ruling that the feature was never active and
   the table holds zero rows.

Decisions live in `docs/adr/` (076–081, plus an addition to 058); this document keeps
no second copy.

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
- The native `PLANNER_PUBLISHED` fanout insert: it constructs no entity, so the factory work must
  not touch it.
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
- The converter decision is contingent on big-bang deployment: switching to rolling deploys
  before this ships reopens it (old pods would write old spellings past conversion), detected at
  review of any deploy-mode change, undone by adding the synonym window the decision rejected.

## Open questions

