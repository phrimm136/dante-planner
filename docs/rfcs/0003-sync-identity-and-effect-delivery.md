---
status: Accepted
tracking: none
---

# 0003 Sync identity and effect delivery

## Scope

Four already-decided backend work streams, transcribed as an implementation contract: a byte-identity
digest on planner content plus the batch pull it enables (ADR 071), a `domain_events` outbox that
carries every observer effect with an eager dispatch and a scheduled relay (ADR 072), removal of the
planner sync SSE channel's backend half (ADR 073), and two new audits plus fleet-safe locking on the
drift reconciler. The decisions are settled; this file is the how. The frontend halves of ADR 071 and
ADR 073, and the `/api/planner/md/events` and `/api/sse/subscribe` streams themselves (which keep
carrying notification events), are out of scope.

---

## Stream 1 — Content digest

Decision: ADR 071.

### Target design

`planner_content.content_digest` is `BINARY(32) NOT NULL`, holding SHA-256 over the content document.
`PlannerContent` is the only writer: the digest is computed at the same point the sync version moves,
over the string the write path received, in UTF-8. No request DTO carries the column and no mapper
sets it.

**Lineage rule — the load-bearing invariant.** The digest is computed exactly once, by the code that
holds the author's bytes, and is adopted unchanged by every later holder. It is never recomputed from
the stored column. MySQL re-serializes a JSON column, so the bytes that come back out of
`planner_content.content` are not the bytes that went in — `PlannerContent.java:108-118` states this
for the searchable-value comparison, and the same fact governs the digest. A consumer that receives
`contentDigest` alongside `content` treats the digest as the identity of those bytes; it must not
hash what it received and expect a match.

The string the write path receives is the output of the request DTO's `@Sanitized(PLANNER_CONTENT)`
bind-time normalization — Jackson re-serialization plus any Tiptap URL rewrite — not the raw request
body, so the digest identifies that normalized string.

Digest recomputation is therefore conditional on the save carrying a content document at all, not on
the document having changed. Every save whose request carried one re-derives the digest from it; a
save carrying none leaves the digest over the string it already identifies. The digest is never
computed from the loaded or stored column, so `loadedContent` cannot answer this question: a client
that pulls a document and saves it back unchanged sends the stored form, and an equality test would
leave the row identifying a string no live copy holds. A byte-identical resave therefore yields the
same digest value, because the derivation is a pure function of the string — not because the write
was skipped.

### Snippets

`PlannerContent` (extends the existing `@PrePersist onCreate` and `recordSave`):

```java
@Column(name = "content_digest", columnDefinition = "BINARY(32)", nullable = false)
private byte[] contentDigest;

@Transient
private boolean contentAssigned;

public void setContent(String content) {
    this.content = content;
    this.contentAssigned = true;
}

private static byte[] digestOf(String content) {
    try {
        return MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
    }
}

public String contentDigestHex() {
    return HexFormat.of().formatHex(contentDigest);
}

@PrePersist
protected void onCreate() {
    if (lastModifiedAt == null) {
        lastModifiedAt = Instant.now();
    }
    contentDigest = digestOf(content);
    contentAssigned = false;
}

public void recordSave() {
    this.syncVersion = this.syncVersion + 1;
    if (contentAssigned) {
        this.contentDigest = digestOf(content);
        this.contentAssigned = false;
    }
}
```

`Planner` aggregate facade, beside the other delegating readers:

```java
public String getContentDigestHex() {
    return content.contentDigestHex();
}
```

Migration `V058__add_planner_content_digest.sql`, one file, three statements:

```sql
ALTER TABLE planner_content ADD COLUMN content_digest BINARY(32) NULL;

UPDATE planner_content SET content_digest = UNHEX(SHA2(content, 256));

ALTER TABLE planner_content MODIFY COLUMN content_digest BINARY(32) NOT NULL;
```

Batch pull on `PlannerQueryController`:

```java
@RateLimited(value = RateLimitPolicy.CRUD, endpoint = "batch")
@PostMapping("/batch")
public ResponseEntity<List<PlannerResponse>> getPlannerBatch(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody PlannerBatchRequest request) {

    return ResponseEntity.ok(plannerQueryService.getPlanners(userId, request.ids()));
}
```

```java
public record PlannerBatchRequest(
    @NotEmpty @Size(max = PlannerConstants.BATCH_PULL_MAX_IDS) List<UUID> ids) {
}
```

```java
@Transactional(readOnly = true)
public List<PlannerResponse> getPlanners(Long userId, List<UUID> ids) {
    Map<UUID, Integer> upvotes = statsRepository.upvoteCounts(ids).stream()
            .collect(Collectors.toMap(PlannerUpvoteRow::getPlannerId, PlannerUpvoteRow::getUpvotes));
    return plannerRepository.findAggregatesForOwner(ids, userId).stream()
            .map(planner -> PlannerResponse.fromEntity(planner,
                    upvotes.getOrDefault(planner.getId(), 0)))
            .toList();
}
```

An id that names no planner, a deleted planner, or another user's planner is absent from the response
array rather than an error; the array is not positionally aligned with the request.

### Change list

1. `backend/src/main/resources/db/migration/V058__add_planner_content_digest.sql` — new, as above.
   Renumber if the branch lands behind another migration.
2. `backend/src/test/resources/db/seed/migration-test-seed.sql` — `planner_content` seed rows gain a
   `content_digest` value (nullability change; required in the same PR).
3. `planner/entity/PlannerContent.java` — add the column field, `digestOf`, `contentDigestHex`;
   extend `onCreate` (line 126-131) and `recordSave` (line 153-155).
4. `planner/entity/Planner.java` — add `getContentDigestHex()` beside the readers at line 121-175.
5. `planner/dto/PlannerResponse.java` — add `String contentDigest` component; `fromEntity` sets
   `.contentDigest(planner.getContentDigestHex())`.
6. `planner/dto/PlannerSummaryResponse.java` — add `String contentDigest`; `fromEntity` uses the
   facade, `from(row)` formats `HexFormat.of().formatHex(row.getContentDigest())`.
7. `planner/repository/PlannerSummaryRow.java` — add `byte[] getContentDigest()`.
8. `planner/repository/PlannerRepository.java` — `findOwnerSummaries` (line 36-44) selects
   `c.contentDigest AS contentDigest`; add `findAggregatesForOwner(Collection<UUID>, Long)` reusing
   `AGGREGATE_LOAD` with `p.id IN :ids`.
9. `planner/repository/PlannerUpvoteRow.java` — new projection (`getPlannerId`, `getUpvotes`).
10. `planner/repository/PlannerStatsRepository.java` — add `upvoteCounts(Collection<UUID>)`.
11. `shared/util/PlannerConstants.java` — add `BATCH_PULL_MAX_IDS`.
12. `planner/dto/PlannerBatchRequest.java` — new.
13. `planner/service/PlannerQueryService.java` — add the batch overload.
14. `planner/controller/PlannerQueryController.java` — add the handler.
15. `planner/validation/SyncVersionValidator.java:24` — close the null hole: a null
    `requestedVersion` on an existing row is rejected, not skipped. Today any client omitting the
    field gets an unconditional overwrite; the web client never omits it, so nothing legitimate
    breaks. New unit test pins the rejection.
16. Delete the planner bookmark write path end to end — the frontend hook is dead code with zero
    production callers, and the backend endpoint loses its only intended consumer. Locate by route
    (the bookmark toggle under `/api/planner/`), remove controller/service/repository members and
    their tests; the read-side `isBookmarked` projection on summaries stays (it renders).

### Test plan

- `PlannerContent` unit test: two saves with byte-identical content leave the digest untouched while
  `syncVersion` moves; a save with changed content moves both; a title-only save moves neither the
  digest nor the document.
- `PlannerResponseContractIT` — the wire contract gains `contentDigest` as 64 lowercase hex chars.
- New IT: save a document, read it back through `GET /api/planner/md/{id}`, and assert the returned
  digest equals SHA-256 of the *request* body, not of the response's `content` field. This is the
  lineage rule as an executable claim.
- New IT: a request body whose `content` carries whitespace Jackson normalizes away — the returned
  digest equals SHA-256 of the sanitizer's output and not of the raw body, pinning which string the
  digest names.
- New IT: save, `GET`, then resave the returned `content` verbatim — the response digest equals
  SHA-256 of that second request's post-sanitization string. A no-op resave is what an equality-based
  recompute rule silently gets wrong, and what RFC 0004's `adoptAck` comparison depends on.
- New IT: `POST /api/planner/md/batch` with a mix of owned, foreign, deleted and unknown ids returns
  only the owned live ones; over `BATCH_PULL_MAX_IDS` ids returns 400.
- Migration smoke test covers the backfill via the updated seed.

---

## Stream 2 — Event outbox

Decision: ADR 072.

### Target design

Every observer effect is derived from a committed `domain_events` row, never from an in-process
event. A raise site writes the row inside the transaction that causes it; the effect is applied in a
separate transaction that also marks the row dispatched. Two triggers reach that dispatch: an eager
after-commit hop for latency, and a scheduled relay for recovery. Both are idempotent through the
`dispatched_at` check under a row lock.

**Pushes follow the dispatch commit, not the arm.** An arm does not publish; it enqueues its pushes,
and the dispatcher fires the queue from a `TransactionSynchronization.afterCommit` registered on the
dispatch transaction. A push sent inside that transaction announces a row a rollback then discards,
and the frontend patches its cache straight from the envelope — so a failed dispatch commit left
recipients holding a notification no query could confirm, and the relay's re-run then delivered it a
second time. What remains after the change is a lost push, recovered by the client's ordinary
refetch on mount, focus, or staleness, against the row the commit made durable.

Payloads are ids only. Everything an effect needs is re-read at dispatch time, so a stale payload
cannot announce a value the database no longer holds. The re-read carries the raise site's own
predicate rather than mere existence: an arm skips a planner that is no longer published, and a
comment since withdrawn, because announcing either would be announcing a state that has passed.

**Poison policy.** `attempts` is incremented in a `REQUIRES_NEW` transaction, so an arm that throws
still leaves the count raised. An increment written in the dispatch transaction is rolled back by
the very failure it exists to record, which leaves a permanently failing row retried forever with
`attempts` frozen at zero. The relay skips rows at or past `OutboxConstants.DISPATCH_ATTEMPT_CAP`,
and the attempt that fails after reaching the cap logs at ERROR and reports to Sentry exactly once.
Capped rows stay in the table: they are the record of what was never derived, and deleting them
destroys the only evidence that it was owed.

The cost of keeping them is paid by the scan. `attempts` is not part of
`idx_domain_events_undispatched`, so a capped row remains at the front of the `ORDER BY created_at`
ordering and is re-read and discarded on every tick, at a cost proportional to how many such rows
exist. That is accepted rather than indexed away: the count is expected to be zero or near it, and a
growing scan cost is itself a signal. Retention is a manual operations action — inspect, then
archive or delete — with no automated sweep and no index change.

Notification rows are derived by `INSERT IGNORE` against `uk_notification_dedup`, for every arm and
not only the published fan-out. The constraint, not a preceding existence check, is what makes a
re-dispatch write nothing the second time — which is the property the relay depends on.

Package placement follows the frozen feature graph: outbox infrastructure lives in
`shared/outbox/{entity,repository,service,scheduler,config}` and carries no feature dependency, and
each effect arm lives in the feature that owns the rows it writes — `planner/effect/` and
`comment/effect/`. Arms reach notification rows through `NotificationDispatchService` (a permitted
cross-feature service edge), never through `NotificationRepository`.

### Snippets

Migration `V059__create_domain_events.sql`:

```sql
CREATE TABLE IF NOT EXISTS domain_events (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(32) NOT NULL,
    aggregate_id  BINARY(16)  NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    dispatched_at DATETIME(6) NULL,
    attempts      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_domain_events_undispatched (dispatched_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

```java
public enum DomainEventType {
    PLANNER_PUBLISHED, PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED
}
```

Payload shapes, keyed by type — `aggregate_id` is the planner in all four:

| type | payload |
|---|---|
| `PLANNER_PUBLISHED` | `{"authorId": <long>}` |
| `PLANNER_RECOMMENDED` | `{"ownerId": <long>}` |
| `COMMENT_RECEIVED` | `{"commentId": <long>}` |
| `REPLY_RECEIVED` | `{"replyId": <long>}` |

```java
public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DomainEvent e WHERE e.id = :id")
    Optional<DomainEvent> findForDispatch(@Param("id") Long id);

    @Query(value = """
            SELECT id FROM domain_events
            WHERE dispatched_at IS NULL AND created_at < :cutoff AND attempts < :cap
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> undispatchedIdsOlderThan(@Param("cutoff") Instant cutoff, @Param("cap") int cap,
            @Param("limit") int limit);
}
```

The scan runs under a read-write `@Transactional` on the service layer rather than untransacted:
without one it inherits `SimpleJpaRepository`'s `readOnly = true`, which is the replica-routing
signal, and a recovery scan against a lagging replica is exactly the wrong reader.

Dispatcher and the arm seam:

```java
@Service
@RequiredArgsConstructor
public class DomainEventDispatcher {

    private final DomainEventRepository events;
    private final DomainEffectRegistry effectRegistry;
    private final DomainEventAttemptRecorder attemptRecorder;
    private final SsePublisher ssePublisher;

    @Transactional
    public void dispatchDomainEvent(long eventId) {
        attemptRecorder.recordAttempt(eventId);
        events.findForDispatch(eventId)
                .filter(event -> !event.isDispatched())
                .ifPresent(event -> {
                    EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
                    effectRegistry.applyEffectFor(event, pushes);
                    event.markDispatched();
                    TransactionSynchronizationManager.registerSynchronization(
                            new EffectPushSynchronization(pushes));
                });
    }
}
```

The attempt is recorded before the row lock is taken, not after: the outer transaction holds an
exclusive lock on the same row for the rest of the dispatch, and a `REQUIRES_NEW` write attempted
behind that lock waits on a transaction that is itself waiting on it.

```java
public interface DomainEffect {

    DomainEventType type();

    void applyEffect(DomainEvent event, EffectPushQueue pushes);
}

@Component
public class DomainEffectRegistry {

    private final Map<DomainEventType, DomainEffect> effectsByType;

    public DomainEffectRegistry(List<DomainEffect> effects) {
        this.effectsByType = effects.stream()
                .collect(Collectors.toUnmodifiableMap(DomainEffect::type, Function.identity()));
    }

    public void applyEffectFor(DomainEvent event, EffectPushQueue pushes) {
        Optional.ofNullable(effectsByType.get(event.getEventType()))
                .orElseThrow(() -> new IllegalStateException(
                        "no effect arm for " + event.getEventType()))
                .applyEffect(event, pushes);
    }
}
```

Single-row derivation, replacing the check-then-save pair in `NotificationDispatchService.deliver`
(`NotificationDispatchService.java:190-208`):

```java
@Modifying
@Query(value = """
        INSERT IGNORE INTO notifications
            (user_id, content_id, notification_type, public_id, planner_id, planner_title,
             comment_snippet, comment_public_id)
        VALUES (:userId, :contentId, :type, UUID_TO_BIN(UUID()), UUID_TO_BIN(:plannerId),
                :plannerTitle, :commentSnippet, UUID_TO_BIN(:commentPublicId))
        """, nativeQuery = true)
int insertIgnore(...);
```

```java
NotificationOutcome raise(Notification notification, SseEventType sseEventType) {
    if (notificationRepository.insertIgnore(...) == 0) {
        return new NotificationOutcome.Duplicate(notification.getUserId(),
                notification.getContentId(), notification.getNotificationType());
    }
    Notification written = notificationRepository
            .findByUserIdAndContentIdAndNotificationType(notification.getUserId(),
                    notification.getContentId(), notification.getNotificationType())
            .orElseThrow();
    return new NotificationOutcome.Delivered(written.getUserId(),
            NotificationEventPayload.fromEntity(written));
}
```

The arms, by type. Every push named below is enqueued on the `EffectPushQueue`, not sent — the
dispatch commit is what releases them:

- `PLANNER_PUBLISHED` (`planner/effect/`) — re-reads through `findPublishedAggregate` and skips a
  planner since withdrawn, then `notificationDispatchService.notifyPlannerPublished(...)` fan-out
  `INSERT IGNORE`, then a `NOTIFY_PUBLISHED` broadcast carrying
  `PlannerPublishedPayload.fromEntity(planner)`.
- `PLANNER_RECOMMENDED` (`planner/effect/`) — the same published re-read, one
  `notifyPlannerRecommended` row, then a `NOTIFY_RECOMMENDED` push on `Delivered`. The event row
  committed with the vote transaction that CAS'd `recommended_notified_at`, so the latch and the
  obligation to notify are atomic; the push no longer depends on a listener firing.
- `COMMENT_RECEIVED` / `REPLY_RECEIVED` (`comment/effect/`) — a comment withdrawn before dispatch is
  skipped, its content being a placeholder by then. Recipient eligibility is decided here (not self,
  notifications enabled), then the notification row and its `NOTIFY_COMMENT` push, then the
  `COMMENT_ADDED` comment-stream push, which happens for every surviving comment including a
  self-comment.

Raise seam, joining the caller's transaction:

```java
@Service
@RequiredArgsConstructor
public class DomainEventRecorder {

    private final DomainEventRepository events;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDomainEvent(DomainEventType type, UUID aggregateId, Map<String, Object> payload) {
        DomainEvent saved = events.insert(DomainEvent.of(type, aggregateId, serializePayload(payload)));
        eventPublisher.publishEvent(new DomainEventRecorded(saved.getId()));
    }
}
```

Eager path — the only `@Async` in the tree:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventEagerDispatch {

    private final DomainEventDispatcher dispatcher;

    @Async("outboxDispatchExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEventRecorded(DomainEventRecorded event) {
        try {
            dispatcher.dispatchDomainEvent(event.eventId());
        } catch (RuntimeException e) {
            log.error("Eager dispatch of domain event {} failed; relay will retry", event.eventId(), e);
        }
    }
}
```

Relay:

```java
@Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
@SchedulerLock(name = "dispatchDomainEvents", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
public void dispatchPendingEvents() {
    Instant cutoff = Instant.now().minus(graceDuration);
    dispatcher.pendingEventIds(cutoff, batchSize).forEach(this::relayOne);
}

private void relayOne(long eventId) {
    try {
        dispatcher.dispatchDomainEvent(eventId);
    } catch (RuntimeException e) {
        log.error("Relay dispatch of domain event {} failed", eventId, e);
        Sentry.captureException(e);
    }
}
```

The lease is `PT30M` rather than `PT5M`: a batch of `outbox.relay.batch-size` rows dispatched while
Redis is degraded spends the command timeout on each push, which puts a full batch well past five
minutes, and a lease that expires mid-batch hands the same batch to a second pod.

Rows the relay stops picking up are the ones at the attempt cap. They are not deleted and not
retried; the ERROR raised as the cap was crossed is what says so, and the rows themselves are what
an operator reads afterwards.

Redis publish hardening in `shared/sse/`. `@Retryable` cannot sit on `SsePublisher.publish(...)` —
it is private and self-invoked, so no proxy sees the call. The retry lands on a one-method
collaborator that `publish` delegates to:

```java
@Component
public class SseChannelSender {

    private final StringRedisTemplate stringRedisTemplate;

    @Retryable(retryFor = RedisConnectionFailureException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2))
    public void send(String topic, String json) {
        stringRedisTemplate.convertAndSend(topic, json);
    }
}
```

`SsePublisher.publish` keeps its `catch (DataAccessException)` for the exhausted case and gains a
distinct arm for the serialization failure:

```java
private static final String UNSERIALIZABLE_COUNTER = "sse.publish.unserializable";

} catch (JsonProcessingException e) {
    meterRegistry.counter(UNSERIALIZABLE_COUNTER, "channel", channel.name()).increment();
    log.error("Failed to serialize SSE envelope for channel {} type {}", channel, envelope.type(), e);
    Sentry.captureException(e);
    return;
}
```

### Change list

1. `db/migration/V059__create_domain_events.sql` and the migration smoke seed — new table.
2. `shared/outbox/entity/DomainEvent.java`, `DomainEventType.java`; `shared/outbox/repository/
   DomainEventRepository.java`; `shared/outbox/service/{DomainEventDispatcher,DomainEffectRegistry,
   DomainEffect,DomainEventRecorder,DomainEventRecorded,DomainEventAttemptRecorder,EffectPushQueue,
   EffectPushSynchronization}.java`; `shared/outbox/scheduler/DomainEventRelay.java` — new.
   `DomainEventDispatcher` also owns `pendingEventIds`, the read-write scan the relay drives.
3. `shared/outbox/config/OutboxAsyncConfig.java` — new: `@EnableAsync`, `@EnableRetry`, and a bounded
   `ThreadPoolTaskExecutor` bean `outboxDispatchExecutor` with `setWaitForTasksToCompleteOnShutdown(true)`.
   `shared/outbox/config/OutboxConstants.java` — new: `DISPATCH_ATTEMPT_CAP`.
4. `backend/build.gradle.kts` — add `org.springframework.retry:spring-retry` and
   `spring-boot-starter-aop` (the Sentry SDK is already present at line 82).
5. `backend/src/main/resources/application.properties` — `outbox.relay.fixed-delay-ms`,
   `outbox.relay.grace`, `outbox.relay.batch-size`.
6. `planner/effect/PlannerPublishedEffect.java`, `PlannerRecommendedEffect.java`;
   `comment/effect/CommentReceivedEffect.java`, `ReplyReceivedEffect.java` — new.
7. `planner/service/PlannerPublishingService.java` — `applyPublish` (line 174-200) records
   `PLANNER_PUBLISHED` instead of publishing `PlannerPublishedEvent`; delete the nested
   `PlannerPublishedEvent` record (line 61-63) and the `onPlannerPublished` listener (line 80-85);
   drop the `ssePublisher`, `notificationDispatchService` and `eventPublisher` fields.
   `applyUnpublish` (line 206-218) records no row — the withdrawal has no observer effect; listed so
   the omission is confirmed rather than assumed.
8. `planner/service/PlannerEngagementService.java` — in `castVote` (line 112-123) the `rowsUpdated > 0`
   branch records `PLANNER_RECOMMENDED` instead of publishing `PlannerRecommendedEvent`.
9. `comment/service/CommentCommandService.java` — `createComment` (line 96-132) and `createReply`
   (line 176-192) each record exactly one event row and drop the inline `notify*` calls, the
   eligibility branching, and `publishCommentCreated`; delete `publishCommentCreated` (line 277-283)
   and the `notificationDispatchService`, `userService`, `eventPublisher` fields it needed.
10. `notification/service/NotificationDispatchService.java` — drop every `@Transactional` annotation
    (the methods run inside the dispatcher's transaction; they must stay untransactional because
    `NotificationOutcome` extends `FailureUnion` and `FailureUnionBoundaryTest` fails the build on a
    transactional method returning one). All four entry points return `NotificationOutcome`. `deliver`
    (line 190-208) becomes `raise`: `INSERT IGNORE` plus read-back, no existence pre-check, and no
    `NotificationRaisedEvent` publish — the arm performs the push.
11. `notification/repository/NotificationRepository.java` — add the single-row `insertIgnore(...)`
    native query and `findByUserIdAndContentIdAndNotificationType(...)`; the existing
    `existsByUserIdAndContentIdAndNotificationType` loses its last caller and goes with it.
12. `notification/service/NotificationOutcome.java` — `Delivered` carries
    `(Long userId, NotificationEventPayload payload)` so the arm can push without re-reading.
13. Delete: `notification/listener/NotificationEventListener.java`,
    `comment/listener/CommentEventListener.java`, `notification/event/NotificationRaisedEvent.java`,
    `planner/event/PlannerRecommendedEvent.java`, `comment/event/CommentCreatedEvent.java`.
14. `shared/sse/SseChannelSender.java` — new; `shared/sse/SsePublisher.java` — delegate the send
    (line 140) and add the unserializable arm (line 134-137). The retry covers
    `RedisConnectionFailureException` and `QueryTimeoutException`: a Lettuce failover surfaces as a
    command timeout, which the connection-acquisition failure does not cover.
15. `architecture/ConventionBaselineTest.java` — `no_async_annotation` (line 53-57) and
    `no_async_enablement_or_thread_pools` (line 59-64) are narrowed by exception list per ADR 067, not
    deleted: the rules stay at full width with `DomainEventEagerDispatch.onDomainEventRecorded` and
    `OutboxAsyncConfig` frozen by name, paired with a staleness test.
16. `backend/src/main/java/org/danteplanner/backend/CLAUDE.md` — the "Deliberately NO `@Async`"
    bullet is restated to the new model with the frozen exception, and the AFTER_COMMIT sentence is
    corrected: the eager hop carries no `REQUIRES_NEW`, because the dispatcher it calls opens a
    transaction of its own.
17. `architecture/EffectPlacementTest.java` — the observer-publish rule's prose and failure message
    describe the outbox model rather than instructing a return to inline listeners, and the rule is
    made non-vacuous. The raise-caller rule admits `..effect..` and `DomainEventDispatcher` only.
18. `notification/repository/NotificationRepository.java` — delete `insert(Notification)`, orphaned
    when `deliver` became `raise`.

### Test plan

- `EffectPlacementIT` rewritten to three claims. Rollback: the comment transaction fails after its
  event write, and afterwards no `domain_events` row, no notification row, and no publish exist.
  Commit: the transaction succeeds, the event row exists, and the notification row plus both pushes
  are derived within the timeout. Crash window: an event row is committed directly and the eager hop
  never runs, then `DomainEventRelay.dispatchPendingEvents()` is invoked with a zero grace and the same
  derivation appears — proving nothing is lost, only delayed.
- `EffectPlacementTest` extended: after-commit listeners reach only `DomainEventDispatcher`;
  `NotificationRepository` write methods are reached only from `..notification..`, and
  `NotificationDispatchService`'s raise methods only from `..effect..` and the dispatcher; every
  `DomainEffect` implementation resides in a `..effect..` package; and no `..effect..` class depends
  on `SsePublisher` at all, which is what makes the transactional-reachability rule true of the
  outbox rather than merely unreached by it.
- `DomainEffectCoverageIT` — injects `List<DomainEffect>` and asserts the declared `type()` set equals
  `DomainEventType.values()`. No freeze list: the tree passes it, so ADR 067's exception mechanism
  applies only if an arm is ever deliberately deferred.
- Dispatcher IT: dispatching the same event id twice writes one notification row and pushes once.
- Dispatcher IT: an arm that throws leaves `attempts` raised, and a second dispatch raises it again —
  the increment survives the rollback of the dispatch that recorded it.
- Relay IT: a row already at `DISPATCH_ATTEMPT_CAP` is absent from the scan, and the attempt that
  reached the cap raised its alarm once.
- `SsePublisherTest` — a `RedisConnectionFailureException` on the first two sends still publishes, a
  `QueryTimeoutException` is retried on the same terms, the exhausted case moves the drop counter
  once, and a serialization failure increments `sse.publish.unserializable` and does not throw.
- `CommentCommandServiceNotificationTest`, `PlannerEngagementServiceTest`,
  `PlannerPublishingServiceTest`, `NotificationServiceLayerTest`, `NotificationDispatchOutcomeTest`,
  `VoteNotificationFlowIT`, `NotificationFanoutIT` — retargeted from the deleted in-process events to
  the recorded event row and the arm.

---

## Stream 3 — Planner SSE removal, backend half

Decision: ADR 073.

### Target design

Planner CRUD raises no SSE event. `SseEventType` carries six constants; `created`, `updated` and
`deleted` no longer exist on the wire. `PlannerCommandService` still builds `PlannerResponse` at each
of the four sites — that value is the HTTP response body and stays.

### Change list

1. Delete `planner/event/PlannerSyncEvent.java` and `planner/service/PlannerSyncEventService.java`.
2. `shared/entity/SseEventType.java` — remove `CREATED`, `UPDATED`, `DELETED` (line 16-18).
3. `planner/service/PlannerCommandService.java` — remove the `eventPublisher.publishEvent(...)` calls
   at lines 291-292, 380-381, 429-430 and 469-470, the `PlannerSyncEvent` and `SseEventType` imports
   (line 20-21), and the now-unused `eventPublisher` field and constructor parameter.
4. `PlannerCommandServiceTest.java` — drop the event assertions at line 208-216 and 348-351 and the
   `never()` publish verifications at lines 234, 256, 374, 464; the surrounding response assertions
   stay.
5. Delete `PlannerSyncEventServiceTest.java` — its premise is gone.
6. `EventPayloadShapeTest.java:94-104` — retarget `clientEvent_WhenUserTargeted_OmitsTheOriginatingDevice`
   to a surviving user-channel type (`NOTIFY_COMMENT`); the claim is about envelope shape, not about
   planner sync.
7. `SseEventTypeMatrixTest.java:34-36` — remove the three rows; the `values()`-derived axis then fails
   if a constant is reintroduced without a row.
8. `SseFanoutIT.java` (lines 96, 101, 152, 156, 181), `RedisConnectionRecoveryIT.java` (lines 284,
   297), `SsePublisherTest.java` (lines 43, 68) — these use the removed constants as arbitrary event
   types; substitute a surviving one.

### Test plan

No new tests. `SseEventTypeMatrixTest` is the gate that the wire surface shrank deliberately, and
`ConventionBaselineTest` plus the compiler catch every stale reference. The frontend contract change
is tracked with ADR 073's frontend half.

---

## Stream 4 — Drift reconciler extensions

Decision: ADR 072 (the recommended audit) and the reconciler's own fleet-safety gap.

### Target design

`PlannerDriftReconciler.reconcile()` runs once per fleet, not once per pod. Three audits join it, all
emitting the existing `DriftRecord(plannerId, kind, expected, actual)` shape through `emit`, and all
repairing nothing.

Catalog scalar copies (`title`, `category`) are compared in SQL with MySQL's NULL-safe equality
negated, under an explicit `COLLATE utf8mb4_0900_bin`. The columns' own `utf8mb4_unicode_ci` is
case-insensitive, accent-insensitive and PAD SPACE, which blinds the comparison to the stale copy
most likely to exist: one left by a rename that changed only capitalization, or one carrying a
trailing space. `utf8mb4_bin` is not sufficient — it is itself PAD SPACE; the `_0900_` form is NO
PAD.

`selected_keywords` is never compared in SQL, but not because the JPA converter's output would
disagree with itself: `KeywordSetConverter` sorts ascending and nulls an empty set on both columns,
so a byte comparison holds for every row it wrote. The reasons are that both columns are also
written from outside JPA — backfill migrations and manual repair — and that `PlannerKeywords`
remaps renamed ids on read, so an array carrying a legacy alias and one carrying its current id
denote the same keywords and differ as strings. Both sides are read raw and normalized in Java
through the same path the runtime uses, `PlannerKeywords.fromStorage(...)`, then compared as `Set`s.

That normalization is total, matching `KeywordSetConverter.convertToEntityAttribute`: a column that
cannot be parsed is compared as the empty set, because the empty set is what a reader of that
planner is served. The abstaining read stays in the filter-index rebuild, where an unreadable column
means the expected state is unknown — treating corrupt as empty is right for saying what a user sees
and wrong for deciding which index rows to delete. Its known limitation: `fromStorage` drops unknown
members and remaps aliases, so drift confined to invalid or legacy members is invisible to the
audit. That is the intended reading — the question is whether the two columns serve the same
keywords, not whether they hold the same bytes.

Both catalog audits carry the class's `c.deleted_at IS NULL` predicate. A tombstoned planner whose
catalog row outlived it is already reported as `catalog_membership`, and comparing its copies on top
turns one bug into three records against the same planner.

### Snippets

```java
@Scheduled(cron = "${planner.reconciler.cron:0 0 4 * * *}")
@SchedulerLock(name = "reconcilePlannerDrift", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
@Transactional(readOnly = true)
public List<DriftRecord> reconcile() {
```

```java
public record CatalogScalarDriftRow(UUID plannerId, String field, String expected, String actual) {
}

public List<CatalogScalarDriftRow> driftedCatalogScalars() {
    return jdbc.query("""
            SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'title' AS field,
                   c.title AS expected, cat.title AS actual
            FROM planner_catalog cat
            JOIN planner_content c ON c.planner_id = cat.planner_id
            WHERE c.deleted_at IS NULL
              AND NOT (cat.title <=> c.title COLLATE utf8mb4_0900_bin)
            UNION ALL
            SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'category' AS field,
                   c.category AS expected, cat.category AS actual
            FROM planner_catalog cat
            JOIN planner_content c ON c.planner_id = cat.planner_id
            WHERE c.deleted_at IS NULL
              AND NOT (cat.category <=> c.category COLLATE utf8mb4_0900_bin)
            """,
            (rs, rowNum) -> new CatalogScalarDriftRow(UUID.fromString(rs.getString("planner_id")),
                    rs.getString("field"), rs.getString("expected"), rs.getString("actual")));
}

public record CatalogKeywordRow(UUID plannerId, String catalogKeywords, String contentKeywords) {
}
```

```java
private List<DriftRecord> auditCatalogScalars() {
    return auditRepository.driftedCatalogScalars().stream()
            .map(row -> new DriftRecord(row.plannerId(), "catalog_" + row.field(),
                    String.valueOf(row.expected()), String.valueOf(row.actual())))
            .toList();
}

private List<DriftRecord> auditCatalogKeywords() {
    return auditRepository.catalogKeywordPairs().stream()
            .map(row -> {
                Set<String> want = keywordsAsServed(row.plannerId(), "content", row.contentKeywords());
                Set<String> have = keywordsAsServed(row.plannerId(), "catalog", row.catalogKeywords());
                return want.equals(have)
                        ? Optional.<DriftRecord>empty()
                        : Optional.of(new DriftRecord(row.plannerId(), "catalog_keywords",
                                String.valueOf(want), String.valueOf(have)));
            })
            .flatMap(Optional::stream)
            .toList();
}

private Set<String> keywordsAsServed(UUID plannerId, String side, String keywordsJson) {
    return parseKeywords(plannerId, side, keywordsJson).orElseGet(Set::of);
}
```

`parseKeywords` gains the planner id and the side so its warn names both; the filter-index rebuild
keeps calling it for its `Optional`, which is the abstaining read.

```sql
SELECT BIN_TO_UUID(s.planner_id) AS planner_id
FROM planner_stats s
LEFT JOIN domain_events e
       ON e.aggregate_id = s.planner_id AND e.event_type = 'PLANNER_RECOMMENDED'
LEFT JOIN notifications n
       ON n.content_id = BIN_TO_UUID(s.planner_id)
      AND n.notification_type = 'PLANNER_RECOMMENDED'
WHERE s.recommended_notified_at IS NOT NULL
  AND s.recommended_notified_at > DATE_SUB(NOW(6), INTERVAL :windowDays DAY)
  AND e.id IS NULL
  AND n.id IS NULL
```

Emitted as kind `recommended_notification`, expected `event or notification row`, actual `neither`.
Both sides must be absent before a record is emitted: an event row aged out of retention after its
notification landed is not drift, and neither is a dispatched event whose recipient deleted the row.

The age bound follows from that same rule rather than being an optimization. Once absence-by-age is
conceded as non-drift, the audit is only sound over stamps young enough that absence still carries
information: every latch taken before the outbox existed has no event row at all, and
`NotificationRetentionService` hard-deletes the notification rows that carried them 465 days after
they were written. Unbounded, the audit converts the entire back catalogue into permanent findings
no repair can clear — arriving on its own as the oldest rows cross the line, not when anyone changes
anything. The window is a named constant on the repository, 30 days, comfortably inside every
retention window in play. The cutoff is computed from the database clock, matching the
`CURRENT_TIMESTAMP(6)` the latch is written with; an `Instant` bound by the driver would be rendered
in the JVM's zone and land hours off on a non-UTC host.

### Change list

1. `planner/service/PlannerDriftReconciler.java` — add `@SchedulerLock` to `reconcile()` (line 85-87);
   add `auditCatalogScalars()`, `auditCatalogKeywords()` and `auditRecommendedNotification()` to the
   `Stream.of(...)` at line 88-93.
2. `planner/repository/PlannerDriftAuditRepository.java` — add `CatalogScalarDriftRow`,
   `CatalogKeywordRow`, `driftedCatalogScalars()`, `catalogKeywordPairs()`,
   `stampedRecommendationsWithoutEffect()` and the audit-window constant, following the class's
   stated naming convention.
3. `PlannerDriftReconcilerSchedulingTest.java` — additive third test asserting `reconcile` carries
   `@SchedulerLock` with a non-blank `name`; the two existing tests are unchanged.

### Test plan

- Reconciler IT: a catalog row whose title diverges from its content row yields exactly one
  `catalog_title` record; a catalog row whose `selected_keywords` differ only in element order yields
  none; one whose keyword *set* differs yields one `catalog_keywords` record.
- Reconciler IT: a catalog title differing from its content row only by case, and one differing only
  by a trailing space, each yield one `catalog_title` record. Both pass under the columns' own
  collation, so this is the test that fails if the explicit `COLLATE` is ever dropped.
- Reconciler IT: a tombstoned planner whose catalog row survives with diverged copies yields
  `catalog_membership` and neither `catalog_title` nor `catalog_keywords`.
- Reconciler IT: a planner whose stored content keywords cannot be parsed yields one
  `catalog_keywords` record comparing the empty set against the catalog's copy, and still yields no
  `entity_filter` or `keyword_filter` record — the two reads of the same corrupt column, held apart.
- Reconciler IT: a `planner_stats` row with `recommended_notified_at` set and neither a
  `PLANNER_RECOMMENDED` event row nor a notification row yields one `recommended_notification` record;
  adding either row clears it; a stamp older than the audit window yields nothing.
- Scheduling test as above.

---

## Migration and rollout

One release window, no coexistence path. Both migrations are additive DDL that an older application
instance tolerates: `content_digest` is written by the new code only, and `domain_events` is unread
by the old. A rolling deploy is therefore safe in one direction — old pods ignore both — but not in
reverse once event rows exist, because a rollback leaves undispatched rows with no relay. Recovery is
to redeploy forward; the relay then drains the backlog on its next tick.

**Backfill lineage caveat.** `UNHEX(SHA2(content, 256))` hashes the *stored* form of every existing
row, which is MySQL's re-serialization rather than the bytes the original author wrote. Those digests
are internally consistent and safe to skip-classify against, because no client still holds the
pre-migration author bytes. The first save of each row after the migration re-establishes true
lineage. Do not treat a pre-migration digest as evidence about author bytes.

**Ordering constraints, as derived rather than assumed:**

- Stream 1 has no edge to any other stream.
- Stream 2 does **not** gate Stream 3, and Stream 3 does not gate Stream 2. The planner sync events
  never travelled through an outbox, their raise sites are in `PlannerCommandService` alone, and the
  effect arms use only surviving `SseEventType` constants (`COMMENT_ADDED`, `NOTIFY_*`). Either order
  works, as does landing them concurrently.
- Stream 4's `recommended_notification` audit reads `domain_events` and must land after V059. Its
  catalog audits and the `@SchedulerLock` addition carry no edge and may land first.
- Within Stream 2, the `ConventionBaselineTest` amendment (item 15) must land in the same commit as
  `OutboxAsyncConfig`, or the build fails on the new `@Async`.
- Migration numbers V058/V059 are next-free at authoring time; if another migration lands first,
  renumber both — the two are order-independent relative to each other.

## Acceptance checklist

- [ ] `content_digest` is `NOT NULL` in production and every row carries 32 bytes.
- [ ] A digest changes only when the document changes; a title-only save leaves it untouched.
- [ ] No code path recomputes a digest from a value read out of `planner_content.content`.
- [ ] `contentDigest` appears on `PlannerResponse` and `PlannerSummaryResponse` as 64 hex chars.
- [ ] `POST /api/planner/md/batch` is rate-limited, bounded by `BATCH_PULL_MAX_IDS`, and returns only
      owned live planners.
- [ ] Every observer effect in the tree is derived from a committed `domain_events` row.
- [ ] Every notification row is derived by `INSERT IGNORE` on the dedup key, with no existence
      pre-check anywhere in the derivation path.
- [ ] Dispatching one event twice produces one notification row and one push.
- [ ] Killing the eager path still delivers every effect within one relay tick.
- [ ] `DomainEventType.values()` and the registered `DomainEffect` arms are the same set.
- [ ] `@Async` appears on exactly one method, named in the `ConventionBaselineTest` freeze list, with
      a live staleness check.
- [ ] `sse.publish.unserializable` increments and reports to Sentry on a serialization failure;
      a transient Redis failure is retried three times before the drop counter moves.
- [ ] `SseEventType` has six constants and `SseEventTypeMatrixTest` covers all six.
- [ ] `PlannerCommandService` publishes no application event and still returns `PlannerResponse` at
      all four sites.
- [ ] `PlannerDriftReconciler.reconcile()` fires once per fleet.
- [ ] The keyword audit is order-insensitive and normalizes both sides through `PlannerKeywords`.
- [ ] Every new audit emits records and repairs nothing.
