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
import org.danteplanner.backend.planner.dto.UpsertResult;
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
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.Stage;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
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

    private final PlannerRepository plannerRepository;
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
        this(plannerRepository, statsRepository, publicationRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, new SimpleMeterRegistry(),
                maxPlannersPerUser, currentSchemaVersion);
    }

    @Autowired
    public PlannerCommandService(
            PlannerRepository plannerRepository,
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
        this(plannerRepository, statsRepository, publicationRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, meterRegistry,
                maxPlannersPerUser, gameDataRegistry.plannerVersions().schemaVersion());
    }

    private PlannerCommandService(
            PlannerRepository plannerRepository,
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

        record Private(Planner planner) implements UpsertTarget {
        }

        record Published(Planner planner) implements UpsertTarget {
        }

        static UpsertTarget of(Optional<Planner> existing) {
            return existing.map(UpsertTarget::held).orElseGet(Absent::new);
        }

        private static UpsertTarget held(Planner planner) {
            return planner.isPublished() ? new Published(planner) : new Private(planner);
        }
    }

    private record SaveAttempt(UpsertPlannerRequest request, UUID deviceId, WriteMode mode, int schemaVersion) {

        PlannerChange changeTo(Planner planner, ObjectMapper mapper) {
            CarriedWrite carried = new CarriedWrite(
                    request.titleOr(planner.getTitle()),
                    request.statusOr(planner.getStatus()),
                    request.category(),
                    request.content(),
                    request.contentVersion(),
                    schemaVersion,
                    deviceId);
            return PlannerChange.between(planner.getContent(), carried, mapper);
        }
    }

    private sealed interface UpsertDecision {

        record Create() implements UpsertDecision {
        }

        record Acknowledge(Planner planner) implements UpsertDecision {
        }

        record UpdatePrivate(Planner planner, PlannerChange change) implements UpsertDecision {
        }

        record UpdatePublished(Planner planner, PlannerChange change) implements UpsertDecision {
        }

        static UpsertDecision of(UpsertTarget target, SaveAttempt attempt, SyncVersionValidator versions,
                ObjectMapper mapper) {
            return switch (target) {
                case UpsertTarget.Absent absent -> new Create();
                case UpsertTarget.Private(Planner planner) -> {
                    PlannerChange change = attempt.changeTo(planner, mapper);
                    yield arbitrated(planner, change, attempt, versions, new UpdatePrivate(planner, change));
                }
                case UpsertTarget.Published(Planner planner) -> {
                    PlannerChange change = attempt.changeTo(planner, mapper);
                    yield arbitrated(planner, change, attempt, versions, new UpdatePublished(planner, change));
                }
            };
        }

        private static UpsertDecision arbitrated(Planner planner, PlannerChange change, SaveAttempt attempt,
                SyncVersionValidator versions, UpsertDecision update) {
            WriteArbitration arbitration = versions.arbitrate(attempt.mode(),
                    PlannerConflictException.Requested.of(attempt.request().syncVersion()),
                    planner.getSyncVersion(), change);
            return switch (arbitration) {
                case ACK_NO_OP -> new Acknowledge(planner);
                case WRITE -> update;
            };
        }
    }

    private void applyUpsertFields(Planner planner, UpsertPlannerRequest request, UUID deviceId,
            PlannerChange change) {
        PlannerContent contentRow = planner.getContent();
        contentRow.setTitle(request.titleOr(contentRow.getTitle()));
        contentRow.setStatus(request.statusOr(contentRow.getStatus()));

        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(planner.getPlannerType(), request.category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }
        PlannerDocument document = switch (change.contentSource()) {
            case STORED -> contentValidator.reparseForCategory(contentRow.getContent(), category,
                    contentValidator.stageOf(planner));
            case CARRIED -> contentValidator.parse(request.content(), category,
                    contentValidator.season(request.contentVersion()), contentValidator.stageOf(planner));
        };
        contentRow.replaceContent(document);
        contentRow.setCategory(category.getValue());

        Set<String> keywords = document.keywords();
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(), keywords));
        contentRow.setSelectedKeywords(keywords);
        contentRow.setDeviceId(deviceIdOr(deviceId, contentRow.getDeviceId()));
        contentRow.setGameContentVersion(request.contentVersion());
        contentRow.setContentSchemaVersion(currentSchemaVersion);
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

    private Planner buildAggregate(UUID id, Room room, CreatableVersion version, MDCategory category,
            PlannerDocument document, UpsertPlannerRequest request, UUID deviceId) {
        Planner planner = Planner.builder()
                .id(id)
                .userId(room.userId())
                .plannerType(version.plannerType())
                .build();
        planner.attach(
                PlannerContent.of(
                        request.titleOr("Untitled"),
                        request.statusOr(PlannerStatus.DRAFT),
                        category.getValue(),
                        document,
                        version.value(),
                        deviceId),
                PlannerPublication.builder().planner(planner).build(),
                PlannerModeration.builder().planner(planner).build());
        return planner;
    }

    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        return createAggregate(userId, deviceId, UUID.fromString(request.id()), request).response();
    }

    UpsertedPlanner createAggregate(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request) {
        accessGuard.requireUser(userId);

        Room room = limitValidator.requireRoom(userId, plannerRepository.countActiveByUserId(userId),
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

        Planner saved = plannerRepository.insert(
                buildAggregate(id, room, version, category, document, request, deviceId));
        statsRepository.insert(PlannerStats.builder().plannerId(saved.getId()).build());
        FreshnessDeclarations.declare(new FreshnessScope.Planner(saved.getId()), new FreshnessScope.User(userId));
        log.info("Created planner {} for user {}", saved.getId(), userId);

        PlannerResponse response = PlannerResponse.fromEntity(saved);

        return new UpsertedPlanner(saved, response, true);
    }

    /**
     * The persisted aggregate of an upsert with its response and whether the planner was created.
     */
    public record UpsertedPlanner(Planner planner, PlannerResponse response, boolean created) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request,
            WriteMode mode) {
        lockForUpsert(userId, id);
        UpsertedPlanner upserted = upsertAggregate(userId, deviceId, id, request, mode);
        return new UpsertResult(upserted.response(), upserted.created());
    }

    WithdrawalOutcome withdraw(Planner planner) {
        return switch (planner.publicationState()) {
            case Publication.Draft draft -> WithdrawalOutcome.NONE;
            case Publication.Unpublished unpublished -> WithdrawalOutcome.NONE;
            case Publication.Published published ->
                    WithdrawalOutcome.of(WriteOutcome.of(publicationRepository.unpublish(planner.getId())));
        };
    }

    void lockForUpsert(Long userId, UUID id) {
        UpsertableId upsertable =
                ownershipValidator.requireUpsertableId(id, userId, plannerRepository.findOwnershipById(id));
        plannerRepository.lockContent(upsertable.plannerId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UpsertedPlanner upsertAggregate(
            Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, WriteMode mode) {
        UpsertTarget target = UpsertTarget.of(plannerRepository.findAggregateForOwner(id, userId));
        admit(target, mode);
        SaveAttempt attempt = new SaveAttempt(request, deviceId, mode, currentSchemaVersion);
        return switch (UpsertDecision.of(target, attempt, syncVersionValidator, objectMapper)) {
            case UpsertDecision.Create create -> createAtAvailableId(userId, deviceId, id, request);
            case UpsertDecision.Acknowledge(Planner planner) -> acknowledge(planner, request);
            case UpsertDecision.UpdatePrivate(Planner planner, PlannerChange change) -> {
                update(planner, attempt, change);
                yield updated(planner);
            }
            case UpsertDecision.UpdatePublished(Planner planner, PlannerChange change) -> {
                update(planner, attempt, change);
                plannerCatalogService.onVisibleEditCommitted(planner);
                FreshnessDeclarations.declare(new FreshnessScope.Global());
                yield updated(planner);
            }
        };
    }

    private void admit(UpsertTarget target, WriteMode mode) {
        switch (target) {
            case UpsertTarget.Absent absent -> { }
            case UpsertTarget.Private(Planner planner) -> {
                logFound(planner, mode);
                restrictionCheck.record(new PrivateWrite());
            }
            case UpsertTarget.Published(Planner planner) -> {
                logFound(planner, mode);
                restrictionCheck.record(restrictionCheck.requireUnrestricted(planner.getUserId()));
            }
        }
    }

    private static void logFound(Planner planner, WriteMode mode) {
        log.info("Planner {} exists for user {}, updating (force={})",
                planner.getId(), planner.getUserId(), mode == WriteMode.FORCED);
    }

    private UpsertedPlanner createAtAvailableId(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request) {
        AvailableId available =
                ownershipValidator.requireIdAvailable(id, userId, plannerRepository.findOwnershipById(id));
        log.info("Planner {} not found, creating for user {}", id, userId);

        restrictionCheck.record(new PrivateWrite());
        return createAggregate(userId, deviceId, available.plannerId(), request);
    }

    private UpsertedPlanner acknowledge(Planner planner, UpsertPlannerRequest request) {
        log.info("Upsert of planner {} would move no field, acknowledging syncVersion {} without a write",
                planner.getId(), planner.getSyncVersion());
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(),
                PlannerKeywords.fromContent(planner.getContentJson()).asSet()));
        return updated(planner);
    }

    private void update(Planner planner, SaveAttempt attempt, PlannerChange change) {
        applyUpsertFields(planner, attempt.request(), attempt.deviceId(), change);
        planner.recordSave();

        log.info("Updated planner {} via upsert, new syncVersion: {}", planner.getId(), planner.getSyncVersion());

        FreshnessDeclarations.declare(new FreshnessScope.Planner(planner.getId()),
                new FreshnessScope.User(planner.getUserId()));
    }

    private static UpsertedPlanner updated(Planner planner) {
        return new UpsertedPlanner(planner, PlannerResponse.fromEntity(planner), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deletePlanner(Long userId, UUID id) {
        LiveOwned owned = ownershipValidator.requireLiveOwnedBy(id, userId, plannerRepository.findOwnershipById(id));
        plannerRepository.lockContent(owned.plannerId());
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        switch (withdraw(planner)) {
            case NONE -> { }
            case WITHDRAWN -> log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete(clock.instant());
        plannerCatalogService.onBecameInvisible(id);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(id), new FreshnessScope.User(planner.getUserId()),
                new FreshnessScope.Global());
        log.info("Soft deleted planner {} for user {}", id, userId);
    }
}
