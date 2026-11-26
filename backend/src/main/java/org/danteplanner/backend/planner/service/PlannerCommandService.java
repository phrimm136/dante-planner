package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.WithdrawalOutcome;
import org.danteplanner.backend.planner.exception.PlannerConcurrentWriteException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.Stage;
import org.danteplanner.backend.planner.repository.ContentWrite;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.planner.sync.CarriedWrite;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator.CreatableVersion;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator.Room;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.AvailableId;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.LiveOwned;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator.UpsertableId;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.planner.sync.PlannerChange;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.danteplanner.backend.planner.sync.WriteArbitration;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.restriction.PrivateWrite;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for a planner owner's CRUD write operations.
 */
@Service
@Slf4j
public class PlannerCommandService {

    private static final String TOP_LEVEL_KEYWORDS_METRIC = "planner_keywords_toplevel_total";

    private static final String CONCURRENT_WRITE_METRIC = "planner_concurrent_write_total";

    private final PlannerRepository plannerRepository;
    private final PlannerContentRepository contentRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerPublicationRepository publicationRepository;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;
    private final PlannerCatalogService plannerCatalogService;
    private final PlannerAccessGuard accessGuard;
    private final RestrictionCheck restrictionCheck;
    private final PlannerCategoryValidator categoryValidator;
    private final PlannerLimitValidator limitValidator;
    private final PlannerOwnershipValidator ownershipValidator;
    private final SyncVersionValidator syncVersionValidator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    private final int maxPlannersPerUser;
    private final int currentSchemaVersion;

    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerContentRepository contentRepository,
            PlannerStatsRepository statsRepository,
            PlannerPublicationRepository publicationRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this(plannerRepository, contentRepository, statsRepository, publicationRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, new SimpleMeterRegistry(),
                maxPlannersPerUser, currentSchemaVersion);
    }

    @Autowired
    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerContentRepository contentRepository,
            PlannerStatsRepository statsRepository,
            PlannerPublicationRepository publicationRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            GameDataRegistry gameDataRegistry) {
        this(plannerRepository, contentRepository, statsRepository, publicationRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, meterRegistry,
                maxPlannersPerUser, gameDataRegistry.plannerVersions().schemaVersion());
    }

    private PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerContentRepository contentRepository,
            PlannerStatsRepository statsRepository,
            PlannerPublicationRepository publicationRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this.plannerRepository = plannerRepository;
        this.contentRepository = contentRepository;
        this.statsRepository = statsRepository;
        this.publicationRepository = publicationRepository;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.plannerCatalogService = plannerCatalogService;
        this.accessGuard = accessGuard;
        this.restrictionCheck = restrictionCheck;
        this.categoryValidator = categoryValidator;
        this.limitValidator = limitValidator;
        this.ownershipValidator = ownershipValidator;
        this.syncVersionValidator = syncVersionValidator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.currentSchemaVersion = currentSchemaVersion;
    }

    private sealed interface UpsertTarget {

        record Absent() implements UpsertTarget {
        }

        record Private(StoredPlanner planner) implements UpsertTarget {
        }

        record Published(StoredPlanner planner) implements UpsertTarget {
        }

        static UpsertTarget of(Optional<StoredPlanner> existing) {
            return existing.map(UpsertTarget::held).orElseGet(Absent::new);
        }

        private static UpsertTarget held(StoredPlanner planner) {
            return switch (planner.publication()) {
                case Publication.Published published -> new Published(planner);
                case Publication.Draft draft -> new Private(planner);
                case Publication.Unpublished unpublished -> new Private(planner);
            };
        }
    }

    private record SaveAttempt(UpsertPlannerRequest request, UUID deviceId, WriteMode mode, int schemaVersion) {

        PlannerChange changeTo(StoredPlanner planner, ObjectMapper mapper) {
            CarriedWrite carried = new CarriedWrite(
                    request.titleOr(planner.content().title()),
                    request.statusOr(planner.content().status()),
                    request.category(),
                    request.content(),
                    request.contentVersion(),
                    schemaVersion,
                    deviceId);
            return PlannerChange.between(planner.content(), carried, mapper);
        }
    }

    private sealed interface UpsertDecision {

        record Create() implements UpsertDecision {
        }

        record Acknowledge(StoredPlanner planner) implements UpsertDecision {
        }

        record UpdatePrivate(StoredPlanner planner, PlannerChange change) implements UpsertDecision {
        }

        record UpdatePublished(StoredPlanner planner, PlannerChange change) implements UpsertDecision {
        }

        static UpsertDecision of(UpsertTarget target, SaveAttempt attempt, SyncVersionValidator versions,
                ObjectMapper mapper) {
            return switch (target) {
                case UpsertTarget.Absent absent -> new Create();
                case UpsertTarget.Private(StoredPlanner planner) -> {
                    PlannerChange change = attempt.changeTo(planner, mapper);
                    yield arbitrated(planner, change, attempt, versions, new UpdatePrivate(planner, change));
                }
                case UpsertTarget.Published(StoredPlanner planner) -> {
                    PlannerChange change = attempt.changeTo(planner, mapper);
                    yield arbitrated(planner, change, attempt, versions, new UpdatePublished(planner, change));
                }
            };
        }

        private static UpsertDecision arbitrated(StoredPlanner planner, PlannerChange change, SaveAttempt attempt,
                SyncVersionValidator versions, UpsertDecision update) {
            WriteArbitration arbitration = versions.arbitrate(attempt.mode(),
                    PlannerConflictException.Requested.of(attempt.request().syncVersion()),
                    planner.content().syncVersion(), change);
            return switch (arbitration) {
                case ACK_NO_OP -> new Acknowledge(planner);
                case WRITE -> update;
            };
        }
    }

    private ContentWrite contentWrite(StoredPlanner planner, SaveAttempt attempt, PlannerChange change,
            Instant now) {
        UpsertPlannerRequest request = attempt.request();
        StoredContent stored = planner.content();
        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(planner.plannerType(), request.category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }
        Stage stage = contentValidator.stageOf(planner.publication());
        PlannerDocument document = switch (change.contentSource()) {
            case STORED -> contentValidator.reparseForCategory(stored.json(), category, stage);
            case CARRIED -> contentValidator.parse(request.content(), category,
                    contentValidator.season(request.contentVersion()), stage);
        };
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(), document.keywords()));
        return new ContentWrite(
                request.titleOr(stored.title()),
                request.statusOr(stored.status()),
                category.getValue(),
                document.keywords(),
                document.json(),
                currentSchemaVersion,
                request.contentVersion(),
                deviceIdOr(attempt.deviceId(), stored.deviceId()),
                stored.syncVersion() + 1,
                now);
    }

    private static @Nullable UUID deviceIdOr(@Nullable UUID carried, @Nullable UUID stored) {
        return carried == null ? stored : carried;
    }

    private enum TopLevelCopy {
        ABSENT("absent"),
        MATCH("match"),
        MISMATCH("mismatch");

        private final String tag;

        TopLevelCopy(String tag) {
            this.tag = tag;
        }

        static TopLevelCopy of(@Nullable Set<String> topLevel, Set<String> derived) {
            if (topLevel == null) {
                return ABSENT;
            }
            return PlannerKeywords.fromClient(topLevel).asSet().equals(derived) ? MATCH : MISMATCH;
        }
    }

    private void countTopLevelCopy(TopLevelCopy copy) {
        meterRegistry.counter(TOP_LEVEL_KEYWORDS_METRIC, "outcome", copy.tag).increment();
    }

    private PlannerConcurrentWriteException concurrentWrite(UUID plannerId, String statement) {
        log.error("Planner {} content row matched no row on {} under its lock", plannerId, statement);
        meterRegistry.counter(CONCURRENT_WRITE_METRIC, "statement", statement).increment();
        return PlannerAccessGuard.changedUnderLock(plannerId);
    }

    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        return PlannerResponse.from(createAggregate(userId, deviceId, UUID.fromString(request.id()), request)
                .planner());
    }

    SaveOutcome createAggregate(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request) {
        UserSummary owner = accessGuard.requireUser(userId);

        Room room = limitValidator.requireRoom(owner, plannerRepository.countActiveByUserId(owner.id()),
                maxPlannersPerUser);
        CreatableVersion version = contentVersionValidator.requireCreatableVersion(
                request.plannerType(), request.contentVersion());
        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(version.plannerType(), request.category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }

        PlannerDocument document = contentValidator.parse(request.content(), category,
                contentValidator.season(version.value()), Stage.DRAFT);
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(), document.keywords()));

        ContentWrite write = new ContentWrite(
                request.titleOr("Untitled"),
                request.statusOr(PlannerStatus.DRAFT),
                category.getValue(),
                document.keywords(),
                document.json(),
                currentSchemaVersion,
                version.value(),
                deviceId,
                1L,
                clock.instant());
        plannerRepository.insertAggregate(
                Planner.created(id, room.userId(), version.plannerType(), write.savedAt()),
                PlannerContent.created(id, write),
                PlannerPublication.draft(id),
                PlannerModeration.clear(id));
        statsRepository.insert(PlannerStats.builder().plannerId(id).build());
        FreshnessDeclarations.declare(new FreshnessScope.Planner(id), new FreshnessScope.User(room.userId()));
        log.info("Created planner {} for user {}", id, room.userId());

        return new SaveOutcome.Created(StoredPlanner.created(id, room.userId(), version.plannerType(), write));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SaveOutcome upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request,
            WriteMode mode) {
        lockForUpsert(userId, id);
        return upsertAggregate(userId, deviceId, id, request, mode);
    }

    WithdrawalOutcome withdraw(StoredPlanner planner) {
        return switch (planner.publication()) {
            case Publication.Draft draft -> WithdrawalOutcome.NONE;
            case Publication.Unpublished unpublished -> WithdrawalOutcome.NONE;
            case Publication.Published published ->
                    WithdrawalOutcome.of(WriteOutcome.of(publicationRepository.unpublish(planner.id())));
        };
    }

    StoredPlanner migrate(StoredPlanner planner, PlannerDocument document, Instant now) {
        StoredPlanner migrated = planner.migrated(document.json(), now);
        switch (WriteOutcome.of(contentRepository.migrateContent(planner.id(), planner.content().syncVersion(),
                document.json(), migrated.content().lastModifiedAt()))) {
            case APPLIED -> FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.id()),
                    new FreshnessScope.User(planner.userId()));
            case NOTHING -> throw concurrentWrite(planner.id(), "migrate");
        }
        return migrated;
    }

    void lockForUpsert(Long userId, UUID id) {
        UpsertableId upsertable =
                ownershipValidator.requireUpsertableId(id, userId, plannerRepository.findOwnershipById(id));
        plannerRepository.lockContent(upsertable.plannerId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SaveOutcome upsertAggregate(
            Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, WriteMode mode) {
        UpsertTarget target = UpsertTarget.of(plannerRepository.findAggregateForOwner(id, userId));
        admit(target, mode);
        SaveAttempt attempt = new SaveAttempt(request, deviceId, mode, currentSchemaVersion);
        return switch (UpsertDecision.of(target, attempt, syncVersionValidator, objectMapper)) {
            case UpsertDecision.Create create -> createAtAvailableId(userId, deviceId, id, request);
            case UpsertDecision.Acknowledge(StoredPlanner planner) -> acknowledge(planner, request);
            case UpsertDecision.UpdatePrivate(StoredPlanner planner, PlannerChange change) ->
                    new SaveOutcome.Updated(update(planner, attempt, change));
            case UpsertDecision.UpdatePublished(StoredPlanner planner, PlannerChange change) -> {
                StoredPlanner saved = update(planner, attempt, change);
                plannerCatalogService.onVisibleEditCommitted(planner, saved);
                FreshnessDeclarations.declare(new FreshnessScope.Global());
                yield new SaveOutcome.Updated(saved);
            }
        };
    }

    private void admit(UpsertTarget target, WriteMode mode) {
        switch (target) {
            case UpsertTarget.Absent absent -> { }
            case UpsertTarget.Private(StoredPlanner planner) -> {
                logFound(planner, mode);
                restrictionCheck.record(new PrivateWrite());
            }
            case UpsertTarget.Published(StoredPlanner planner) -> {
                logFound(planner, mode);
                restrictionCheck.record(restrictionCheck.requireUnrestricted(planner.userId()));
            }
        }
    }

    private static void logFound(StoredPlanner planner, WriteMode mode) {
        log.info("Planner {} exists for user {}, updating (force={})",
                planner.id(), planner.userId(), mode == WriteMode.FORCED);
    }

    private SaveOutcome createAtAvailableId(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request) {
        AvailableId available =
                ownershipValidator.requireIdAvailable(id, userId, plannerRepository.findOwnershipById(id));
        log.info("Planner {} not found, creating for user {}", id, userId);

        restrictionCheck.record(new PrivateWrite());
        return createAggregate(userId, deviceId, available.plannerId(), request);
    }

    private SaveOutcome acknowledge(StoredPlanner planner, UpsertPlannerRequest request) {
        log.info("Upsert of planner {} would move no field, acknowledging syncVersion {} without a write",
                planner.id(), planner.content().syncVersion());
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(),
                PlannerKeywords.fromContent(planner.content().json()).asSet()));
        return new SaveOutcome.Acknowledged(planner);
    }

    private StoredPlanner update(StoredPlanner planner, SaveAttempt attempt, PlannerChange change) {
        ContentWrite write = contentWrite(planner, attempt, change, clock.instant());
        switch (WriteOutcome.of(contentRepository.replaceAtVersion(planner.id(), planner.content().syncVersion(),
                write))) {
            case APPLIED -> { }
            case NOTHING -> throw concurrentWrite(planner.id(), "replace");
        }
        log.info("Updated planner {} via upsert, new syncVersion: {}", planner.id(), write.syncVersion());

        FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.id()),
                new FreshnessScope.User(planner.userId()));
        return planner.saved(write);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deletePlanner(Long userId, UUID id) {
        LiveOwned owned = ownershipValidator.requireLiveOwnedBy(id, userId, plannerRepository.findOwnershipById(id));
        plannerRepository.lockContent(owned.plannerId());
        StoredPlanner planner = accessGuard.findPlannerOrThrow(userId, id);

        switch (withdraw(planner)) {
            case NONE -> { }
            case WITHDRAWN -> log.info("Auto-unpublished planner {} before deletion", id);
        }

        switch (WriteOutcome.of(contentRepository.markDeleted(id, clock.instant()))) {
            case APPLIED -> { }
            case NOTHING -> throw PlannerAccessGuard.absent(id);
        }
        plannerCatalogService.onBecameInvisible(id);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(id), new FreshnessScope.User(planner.userId()),
                new FreshnessScope.Global());
        log.info("Soft deleted planner {} for user {}", id, userId);
    }
}
