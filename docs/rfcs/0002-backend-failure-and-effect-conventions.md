---
status: Accepted
tracking: 243
---

# 0002 Backend failure, effect, and enforcement conventions

## Summary

Establish backend-wide conventions for where failures travel as values versus exceptions, which
side of a transaction commit each side effect belongs on, how cross-cutting checks attach to
endpoints, and where a null may carry meaning — enforced structurally (architecture tests plus
one runtime backstop) where a rule can see the violation, by review where it cannot. The confirmed defect sites are converted as the proof of each
convention. FP constructs stay auxiliary: sealed failure unions and closed state sets only;
decomposition remains OOP, cross-cutting enforcement remains interception.

## Motivation

Every confirmed defect class shares one shape: a call convention where a structural guarantee
should be. Hand-placed rate-limit calls leave two dozen endpoints unguarded with nothing detecting
the omission. Transactional methods publish SSE and notification events inline, so a rollback can
announce rows that never committed. A rate-limited OAuth callback is swallowed into the generic
login-error redirect, indistinguishable from declined consent. The publish/unpublish seam still
branches on the boolean its intent-method refactor set out to remove. One Jackson type-check idiom
is copied across validators. After this lands, each of these classes is either impossible to
reintroduce (build failure) or fails loudly at runtime.

## Current behavior

- Rate limiting: 33 `rateLimitService.check(...)` calls across 11 controllers, always the
  handler's first statement; 57 mapped handlers, so 24 carry no check. No annotation, interceptor,
  or aspect exists. `AuthController` invokes the check inside a blanket `catch (Exception)`, so
  `RateLimitExceededException` becomes a generic login-error redirect.
- Effects: four `@TransactionalEventListener(AFTER_COMMIT)` listeners exist, but
  `PlannerCommandService`, `CommentCommandService`, `UserModerationService`, and
  `NotificationDispatchService` publish Redis/SSE events inline within transactions.
  `UserAccountLifecycleService.deleteAccount` revokes tokens (Redis) before commit;
  `performHardDelete` repeats the revocation inside a swallowing try/catch, defensible only
  because the soft-delete revocation already ran.
- Publish seam: one endpoint takes `{published: boolean}`; `PlannerPublishingService` re-tests
  the boolean it forwards; `Planner.publish()`/`unpublish()` reach `PlannerPublication.setPublished(boolean)`
  by asymmetric routes; the moderator path collapses `PublicationChange` back to a boolean;
  validation runs after mutation; `PublishRequest` documents a legacy no-body toggle its own
  `@NotNull` makes unreachable.
- Validation: `IdReferenceValidator` and `PlannerContentEntityExtractor` repeat the
  `if (x == null || !x.isObject())` Jackson idiom (sixteen occurrences in the former);
  `ValidationErrors` declares its messages byte-identical API contract.
  `IdReferenceValidator` silently skips a present-but-non-array field because
  `StructuralValidator` already rejected the type error — one error per defect, kept.
- Failure shapes: `RotationResult` is a working sealed union (Java 21) with an exhaustive
  switch consumer; `UserService` branches on `DataIntegrityViolationException` cause-chain
  introspection for username-suffix retries; `NotificationDispatchService` suppresses duplicates
  by swallowing the same exception.
- Null handling: `TokenBlacklistService.invalidateUserTokens` silently returns on a null user id;
  `revokeLogoutSession` takes five positional parameters, any of which may be null to skip;
  `isBlacklisted`/`isUserTokenInvalidated` answer false for null inputs the authentication filter
  never sends. `UserSettingsService.updateSettings` treats null fields as unchanged —
  wire-delivered meaning, kept.

## Prior art

- Frontend ADRs (`@errors`): failures travel as one discriminated union per pipeline with `throw`
  only in boundary adapters; the union stops where a library owns control flow (TanStack Query),
  through exactly one adapter; classifiers are pure functions with reactions living at consumers.
  This proposal transplants all three rules to the backend's control-flow owners.
- In-repo: `RotationResult` proves the sealed-union shape on Java 21 with zero dependencies.
  `FeatureBoundaryTest` proves freeze-with-allowlist architecture enforcement.
  `RateLimitKeyFormatTest` proves key spellings as pinned production contract.
- External: functional core / imperative shell; write-ahead ordering as the atomicity substitute
  between stores that share no transaction (MySQL and Redis here).

## Proposal

Five conventions, each with its enforcement:

1. **Failure channels by jurisdiction.** A framework that owns control flow defines the failure
   currency inside its jurisdiction: Spring MVC's is the exception routed to the global handler;
   the transaction proxy's is the unchecked throw that triggers rollback. Sealed failure unions
   (marker-tagged) exist only where a JVM-internal caller branches on outcomes: pure-decision
   seams, seams whose state is not JPA-managed, or non-transactional facades. A union never
   crosses the transaction proxy; an architecture test enforces this.

   The shape the rule checks against (names illustrative; the marker and the sealed closure are
   the argument):

   ```java
   interface FailureUnion { }  // marker the architecture test matches on

   sealed interface CreateOutcome extends FailureUnion
           permits Created, SuffixCollision { }
   record Created(User user) implements CreateOutcome { }
   record SuffixCollision(String candidate) implements CreateOutcome { }
   ```

   An exhaustive `switch` over the union fails compilation when a variant is added unhandled;
   the architecture test fails the build when any `@Transactional` method declares a
   `FailureUnion` subtype as its return type.
2. **Effect placement by affordable failure direction.** Observers follow the commit: any effect
   that must never announce uncommitted state runs in an after-commit listener, and a missed run
   must be recoverable from committed state. Guards precede the commit: any effect that must never
   arrive late (token revocation) runs inline, ordered so its failure aborts the transaction. An
   architecture test bans observer publishes inside transactional methods, with guard calls
   allowlisted.
3. **Rate limiting as a declarative seam.** A `@RateLimited` annotation carries the existing
   policy enum (attachment point and bucket identity stay decoupled); class-level default with
   method-level override; an interceptor executes the check before the handler. Coverage is a
   build-failing architecture rule — every handler resolves a policy or an explicit exemption —
   backstopped by a deny-and-log branch for the case where the build gate did not run. The OAuth
   callback's rate-limit outcome becomes a distinct redirect code.
4. **Validation traversal as a deep module.** A traversal-helper module owns all dynamic-JSON
   shape branching — one internal iterator per repeated shape (flat string arrays, object
   arrays), null-object navigation for single-field access — and emits the contractual
   messages; validators receive only valid elements plus their index and keep domain membership
   checks. Per-field value checks (ranges) keep their shape. Messages stay byte-identical,
   frozen by a golden corpus recorded before conversion.

   The collapse is the argument — today's per-validator shape, repeated nine-plus-seven times:

   ```java
   JsonNode array = root.get(fieldName);
   if (array == null || !array.isArray()) return;
   Set<String> seen = new HashSet<>();
   for (int i = 0; i < array.size(); i++) {
       JsonNode node = array.get(i);
       if (!node.isTextual()) { context.reject(fieldName + "[" + i + "]", /* type error */); continue; }
       if (!seen.add(node.asText())) { context.reject(fieldName, /* duplicate */); continue; }
       // domain check
   }
   ```

   becomes, with type and uniqueness branching owned once by the helper:

   ```java
   eachUniqueString(root, fieldName, context, (giftId, i) -> {
       if (!gameDataRegistry.hasEgoGift(giftId)) {
           context.reject(fieldName, p -> ValidationErrors.invalidIdReference(p, giftId));
       }
   });
   ```

5. **Null handling.** Nulls die once at the web boundary via bean validation; internal defensive
   re-checks are removed rather than standardized. Null carries meaning only where the wire
   delivers it (partial-update DTOs); internal APIs express absence structurally — an absent
   element, an overload, a typed item — never a null argument. A null reaching a mutation is a
   bug and throws; only queries may answer their negative identity, and only where documented.
   Applied here: user-token invalidation throws on a null user id, and logout revocation accepts
   only the credentials actually present, as typed non-null items in one atomic call — absence
   is exclusion, not a null slot. Review-enforced; build-time nullness checking is a non-goal.

The publish seam completes its intent-method refactor: two intent endpoints, the boolean
eliminated at every layer, validation before mutation, and the legacy route delegating for exactly
one release.

## Decomposition

```
- golden-corpus — recorded snapshot of validator error output over a bad-input corpus
- validator-traversal — traversal helper owns type/uniqueness branching; validators converted (after: golden-corpus)
- rate-limit-seam — declarative policy attachment, interceptor, coverage rule, runtime backstop, distinct callback code
- effect-placement — observer publishes moved after commit, guards stay inline, allowlisted architecture rule
- publish-intent-seam — intent endpoints, boolean eliminated, validate-first, one-release delegate
- failure-unions — failure marker, transaction-boundary rule, internal branching seams converted (after: effect-placement)
- null-boundary — guard no-ops on null become throws, logout revocation takes typed present-only items, defensive query re-checks deleted
```

## Scenarios

```gherkin
Scenario: Mixed-type gift array accumulates both errors
  Given planner content whose selectedGiftIds is ["gift_a", 42, "gift_a"]
  When gift-id reference validation runs over the field
  Then exactly two errors are produced, in order: INVALID_FIELD_TYPE with message
       "Field 'selectedGiftIds[1]' must be string, got number 42", then DUPLICATE_VALUE
       with message "Duplicate value 'gift_a' in selectedGiftIds"
```
Home: golden-corpus.

```gherkin
Scenario: Converted validators replay the corpus byte-identically
  Given the golden corpus recorded before conversion
  When the converted validators run over every corpus input
  Then every error code, message, and error order is byte-identical to the snapshot
```
Home: validator-traversal (consumer of golden-corpus).

```gherkin
Scenario: Bare endpoint fails the build
  Given a request-handler method with no rate-limit declaration on itself or its class
  When the architecture tests run
  Then the build fails and the failure message names the handler method
```
```gherkin
Scenario: Backstop denies when the gate was skipped
  Given a deployed handler carrying no rate-limit declaration
  When any request reaches it
  Then the response is 500, an error-level log names the handler, and the handler body never runs
```
```gherkin
Scenario: Rate-limited OAuth callback is distinguishable
  Given the AUTH bucket for a client identifier is exhausted
  When the OAuth callback is requested
  Then the redirect carries a rate-limit-specific error code distinct from the declined-consent code
```
Home: rate-limit-seam.

```gherkin
Scenario: Rollback announces nothing
  Given a comment-creation transaction that fails after its notification step
  When the transaction rolls back
  Then zero SSE events were published and zero notification rows exist
```
```gherkin
Scenario: Crash after commit delays but never invents
  Given a commit has landed and the process dies before its after-commit listener runs
  When the recipient next fetches notifications
  Then the committed notification row is returned, and no subscriber received any event for uncommitted data
```
```gherkin
Scenario: Redis outage aborts account deletion
  Given token revocation throws during account deletion
  When the deletion transaction ends
  Then it has rolled back, the account remains active, and the caller receives the typed 503 degradation code
```
Home: effect-placement.

```gherkin
Scenario: Publish validates before mutating
  Given a draft planner with a blank title
  When the owner requests publish
  Then the response is 400 with a validation error code and the planner's stored state is unchanged
```
```gherkin
Scenario: Stale client publishes through the delegate
  Given a client running the previous release during the delegate window
  When it calls the legacy publish route
  Then the planner is published and the response is identical to the intent endpoint's response
```
```gherkin
Scenario: Delegate dies on schedule
  Given the release after the delegate window
  When the legacy publish route is called
  Then the response is 404
```
Home: publish-intent-seam.

```gherkin
Scenario: Failure value cannot escape a transaction
  Given a @Transactional method whose return type implements the failure marker
  When the architecture tests run
  Then the build fails and the failure message names the method
```
```gherkin
Scenario: Duplicate notification suppressed as a value
  Given dispatch invoked from the after-commit listener path for an already-notified event
  When dispatch runs
  Then the outcome value is Duplicate, no new notification row exists, and no exception is caught or logged
```
Home: failure-unions (second scenario consumes effect-placement's listener path).

```gherkin
Scenario: Guard invoked with null throws
  Given user-token invalidation called with a null user id
  When it executes
  Then it throws NullPointerException and no invalidation stamp is written
```
```gherkin
Scenario: Logout with only an access token revokes exactly it
  Given an authenticated logout request carrying an access token and no refresh cookie
  When logout completes
  Then the access token is blacklisted with immediate effect and no refresh-family revocation occurs
```
Home: null-boundary.

## Invariants

- No `@Transactional` method returns a failure-marker type — gate: architecture test.
- Every request handler resolves a rate-limit policy or an explicit exemption — gates:
  architecture test and the runtime deny-and-log backstop.
- At no crash point does a soft-deleted account coexist with unrevoked tokens — gates:
  guard-before-commit ordering and an integration test failing Redis mid-deletion.
- `@Transactional` methods invoke no observer publishers — gate: architecture test with
  allowlisted guard calls.
- Validator messages are byte-identical across the conversion — gate: golden corpus.
- The legacy publish route exists for exactly one release — gate: tracked removal task in the
  following release.

## Decisions

Recorded in `docs/adr/` (047–051); the bullets there are authoritative, and changes during
implementation proceed by supersession. Cited by address:

- 047 @errors @transactions — failure unions never cross the transaction proxy; rollback is an
  unchecked throw.
- 047 @errors @boundary — exceptions to the global handler at the web boundary; one adapter per
  jurisdiction.
- 047 @errors @library — Java 21 sealed unions; no FP library.
- 048 @effects @observers — observer effects follow the commit.
- 048 @effects @guards — guard effects precede the commit and abort it on failure.
- 049 @ratelimit @identity — the annotation carries the policy enum; attachment decoupled from
  identity.
- 049 @ratelimit @enforcement — coverage fails the build; deny-and-log runtime backstop.
- 049 @ratelimit @auth-callback — rate-limited callback redirects with a distinct code.
- 050 @validation @traversal — type and uniqueness branching in one internal iterator.
- 050 @validation @classifiers — classifiers pure; reactions with consumers.
- 051 @wire @unpublish — two intent endpoints; one-release delegate with tracked deletion.
- 052 @nulls @boundary — nulls die once at the web boundary; internal defensive re-checks removed.
- 052 @nulls @meaning — internal APIs express absence structurally, never as a null argument.
- 052 @nulls @revocation — logout revocation takes typed present-only items in one atomic call.
- 052 @nulls @effects — a null reaching a mutation throws; queries answer negative identity only
  where documented.

## Drawbacks

- Sealed unions add per-seam type surface; outside branching seams they are pure ceremony. The
  marker rule bounds where they may appear, not how many appear.
- Architecture-test allowlists (guard effects, boundary exceptions) require curation; a stale
  allowlist is quiet permission.
- Rate-limit endpoint keys move from code arguments into annotation strings; the key-format test
  carries the typo risk.
- After-commit listeners run outside the request context, weakening log correlation for moved
  publishes unless the event carries it.
- The null convention has no build gate: no architecture rule can distinguish a wire-meaning
  null branch from a defensive one, so it is review-enforced until nullness checking is adopted.

## Non-goals

- Token rotation internals (the Lua protocol with three parsers, redundant crypto, test-only
  production members) — persists until its own proposal.
- Access-guard decomposition — the loader/guard ambiguity persists at its call sites.
- The blacklist/rotation bidirectional coupling — persists; the failure-unions node must not
  touch it.
- Redis key-builder consolidation — the hand-rolled key formats persist.
- Frontend dead bookmark code — direct-lane cleanup, separately issued.
- Build-time nullness checking (NullAway or similar) — the structural enforcement of the null
  convention; earns its own proposal.

## Risks and rollback

- Effect moves change event timing; each site is independently revertible by re-inlining one
  publish. Detection: the SSE drift reconciler and error tracking.
- The interceptor must reproduce existing bucket-key spellings exactly or live buckets silently
  reset; the key-format test gates this, and the limiter service itself is untouched, so rollback
  is re-inlining calls.
- Failing to delete the legacy publish delegate in the following release converts a coexistence
  window into an untracked compatibility layer; the tracking issue carries the removal as a
  checklist item.
- Nothing here is permanently unundoable; the closest is bucket-key drift, which self-heals as
  buckets refill but grants a one-window fresh allowance if it occurs.

## Open questions

None.
