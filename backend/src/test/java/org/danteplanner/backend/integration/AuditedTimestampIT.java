package org.danteplanner.backend.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.danteplanner.backend.planner.entity.PlannerSubscriptionId;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.SaveOutcome;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.shared.config.JpaAuditingConfig;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.MappingSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, AuditedTimestampIT.SettableClockConfig.class})
class AuditedTimestampIT extends SharedMySqlContainerSupport {

    private static final Instant T = Instant.parse("2026-03-04T05:06:07Z");
    private static final Instant SUBJECT_TIME = T.minus(Duration.ofDays(1));
    private static final Instant LATER = T.plus(Duration.ofHours(1));
    private static final Instant PRECISE = Instant.parse("2026-03-04T05:06:07.123456789Z");
    private static final Instant PRECISE_MICROS = Instant.parse("2026-03-04T05:06:07.123456Z");
    private static final UUID DEVICE = UUID.fromString("0b5d1f2e-0000-4000-8000-00000000abcd");

    @Autowired
    private SettableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerPublicationRepository publicationRepository;

    @Autowired
    private PlannerCommandService plannerCommandService;

    @Autowired
    private PlannerSubscriptionRepository subscriptionRepository;

    @Autowired
    private PlannerSubscriptionService subscriptionService;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private DomainEventRepository domainEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        clock.set(T);
        owner = TestDataFactory.createTestUser(userRepository, "audited@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @Test
    void newComment_WhenTheClockIsFixed_IsStampedWithItsInstant() {
        Long id = transactionTemplate.execute(status -> commentRepository.insert(
                new PlannerComment(planner.id(), owner.getId(), null, 0)).getId());

        PlannerComment stored = commentRepository.findById(id).orElseThrow();

        assertThat(stored.getCreatedAt()).isEqualTo(T);
        assertThat(stored.getPublicId()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("auditedRows")
    void newRow_WhenTheClockIsFixed_IsStampedWithItsInstant(BiFunction<Long, StoredPlanner, Object> row) {
        Object unsaved = row.apply(owner.getId(), planner);
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(unsaved));

        assertThat(storedCreatedAt(unsaved)).isEqualTo(T);
    }

    @Test
    void newSubscription_WhenTheClockIsFixed_IsStampedWithItsInstant() {
        subscriptionService.createSubscription(owner.getId(), planner.id());

        PlannerSubscription stored = subscriptionRepository
                .findById(new PlannerSubscriptionId(owner.getId(), planner.id())).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(T);
    }

    @Test
    void newCommentReport_WhenTheClockIsFixed_IsStampedWithItsInstant() {
        PlannerComment comment = commentRepository.save(
                new PlannerComment(planner.id(), owner.getId(), null, 0));
        PlannerCommentReport report = new PlannerCommentReport(comment.getId(), owner.getId(), "SPAM");

        transactionTemplate.executeWithoutResult(status -> entityManager.persist(report));

        assertThat(storedCreatedAt(report)).isEqualTo(T);
    }

    @Test
    void newDomainEvent_WhenTheClockIsFixed_IsStampedWithItsInstantAndKeepsItAcrossAClaim() {
        DomainEvent event = DomainEvent.of(DomainEventType.PLANNER_PUBLISHED, planner.id(), "{}");

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(event);
            assertThat(domainEventRepository.claim(event.getId(), LATER)).isEqualTo(1);
        });

        assertThat(storedCreatedAt(event)).isEqualTo(T);
    }

    private static Stream<Named<BiFunction<Long, StoredPlanner, Object>>> auditedRows() {
        return Stream.of(
                Named.of("vote", (userId, planner) -> new PlannerVote(userId, planner.id(), VoteType.UP)),
                Named.of("view", (userId, planner) -> new PlannerView(planner.id(),
                        ViewerHashUtil.hashForAuthenticatedUser(userId, planner.id()), LocalDate.of(2026, 3, 4))),
                Named.of("report", (userId, planner) -> new PlannerReport(userId, planner.id())),
                Named.of("notification", (userId, planner) -> new Notification(
                        userId, planner.id().toString(), NotificationType.COMMENT_RECEIVED)),
                Named.of("moderation action", (userId, planner) -> ModerationAction.builder()
                        .actionType(ModerationActionType.TIMEOUT)
                        .actorId(userId)
                        .targetUuid(planner.id().toString())
                        .targetType(ModerationTargetType.USER)
                        .build()));
    }

    private Object storedCreatedAt(Object row) {
        Object id = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(row);
        return transactionTemplate.execute(status ->
                ReflectionTestUtils.getField(entityManager.find(row.getClass(), id), "createdAt"));
    }

    @Test
    void newPlanner_WhenSavedAtANanosecondInstant_StampsBothColumnsAtTheMicrosecondTheResponseCarries() {
        clock.set(PRECISE);
        UUID id = UUID.randomUUID();

        SaveOutcome created = save(UpsertRequests.draft().id(id.toString()).build());
        PlannerResponse response = PlannerResponse.from(created.planner());
        StoredPlanner read = plannerRepository.findAggregate(id).orElseThrow();

        assertThat(created).isInstanceOf(SaveOutcome.Created.class);
        assertThat(response.lastModifiedAt()).isEqualTo(PRECISE_MICROS);
        assertThat(response.createdAt()).isEqualTo(PRECISE_MICROS);
        assertThat(response.syncVersion()).isEqualTo(1L);
        assertThat(read.content().lastModifiedAt()).isEqualTo(response.lastModifiedAt());
        assertThat(read.createdAt()).isEqualTo(response.createdAt());
    }

    @Test
    void savedPlanner_WhenSavedAgainLater_MovesLastModifiedAtAndKeepsCreatedAt() {
        clock.set(PRECISE);
        UUID id = UUID.randomUUID();
        StoredPlanner created = save(UpsertRequests.draft().id(id.toString()).build()).planner();
        clock.set(LATER);

        PlannerResponse response = PlannerResponse.from(
                save(UpsertRequests.resending(created).title("Renamed").build()).planner());
        StoredPlanner read = plannerRepository.findAggregate(id).orElseThrow();

        assertThat(response.lastModifiedAt()).isEqualTo(LATER).isEqualTo(read.content().lastModifiedAt());
        assertThat(response.syncVersion()).isEqualTo(2L).isEqualTo(read.content().syncVersion());
        assertThat(read.createdAt()).isEqualTo(PRECISE_MICROS);
    }

    @Test
    void savedPlanner_WhenAStaleResendMovesNothing_AnswersTheStoredStampAndVersion() {
        clock.set(PRECISE);
        UUID id = UUID.randomUUID();
        StoredPlanner created = save(UpsertRequests.draft().id(id.toString()).build()).planner();
        clock.set(LATER);
        StoredPlanner renamed = save(UpsertRequests.resending(created).title("Renamed").build()).planner();
        clock.set(LATER.plus(Duration.ofHours(1)));

        SaveOutcome resent = save(UpsertRequests.resending(renamed).syncVersion(1L).build());

        assertThat(resent).isInstanceOf(SaveOutcome.Acknowledged.class);
        assertThat(PlannerResponse.from(resent.planner()).lastModifiedAt()).isEqualTo(LATER);
        assertThat(resent.planner().content().syncVersion()).isEqualTo(2L);
        assertThat(lastModifiedAt(id)).isEqualTo(LATER);
    }

    @Test
    void contentRow_WhenOnlyASiblingTableChanges_KeepsLastModifiedAt() {
        Instant before = lastModifiedAt(planner.id());
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                publicationRepository.setOwnerNotifications(planner.id(), false));

        StoredPlanner stored = plannerRepository.findAggregate(planner.id()).orElseThrow();
        assertThat(stored.ownerNotifications()).isEqualTo(NotificationSetting.OFF);
        assertThat(stored.content().lastModifiedAt()).isEqualTo(before);
    }

    private SaveOutcome save(UpsertPlannerRequest request) {
        return plannerCommandService.upsertPlanner(owner.getId(), DEVICE, UUID.fromString(request.id()), request,
                WriteMode.CHECKED);
    }

    @Test
    void userRow_WhenItChanges_MovesUpdatedAtAndKeepsCreatedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                userRepository.changeEpithet(owner.getId(), "ASTUTE", clock.instant()));

        User stored = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(T);
        assertThat(stored.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void newUser_WhenTheClockIsFixed_IsStampedInBothColumns() {
        User stored = userRepository.findById(owner.getId()).orElseThrow();

        assertThat(stored.getCreatedAt()).isEqualTo(T);
        assertThat(stored.getUpdatedAt()).isEqualTo(T);
    }

    @Test
    void newRow_WhenOtherContextsStartedLast_IsStampedByItsOwnContext() {
        List<AnnotationConfigApplicationContext> others = new ArrayList<>();
        try (AnnotationConfigApplicationContext subject = jpaContext(SUBJECT_TIME, () -> {
            others.add(jpaContext(LATER, () -> { }, JpaAuditingConfig.class));
            others.add(jpaContext(LATER.plusSeconds(60), () -> { }, JpaAuditingConfig.class));
        }, JpaAuditingConfig.class)) {
            others.add(jpaContext(LATER.plusSeconds(120), () -> { }, JpaAuditingConfig.class));

            assertThat(commentSavedThrough(subject).getCreatedAt()).isEqualTo(SUBJECT_TIME);
        } finally {
            others.forEach(AnnotationConfigApplicationContext::close);
        }
    }

    @Test
    void newRow_WhenOtherContextsWereClosed_IsStampedByItsOwnContext() {
        Runnable startAndClose = () -> {
            jpaContext(LATER, () -> { }, JpaAuditingConfig.class).close();
            jpaContext(LATER.plusSeconds(60), () -> { }, JpaAuditingConfig.class).close();
        };
        try (AnnotationConfigApplicationContext subject =
                jpaContext(SUBJECT_TIME, startAndClose, JpaAuditingConfig.class)) {
            assertThat(commentSavedThrough(subject).getCreatedAt()).isEqualTo(SUBJECT_TIME);
        }
    }

    @Test
    void newRow_WhenTheContextHasNoAuditing_FailsTheSave() {
        try (AnnotationConfigApplicationContext unaudited = jpaContext(SUBJECT_TIME, () -> { })) {
            assertThatThrownBy(() -> commentSavedThrough(unaudited))
                    .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                            .isInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasMessageContaining("AuditingHandler"));
        }
    }

    private static AnnotationConfigApplicationContext jpaContext(Instant time, Runnable beforeTheFactoryIsBuilt,
            Class<?>... configurations) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Clock.class, () -> Clock.fixed(time, ZoneOffset.UTC));
        context.registerBean(HibernateJpaVendorAdapter.class);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            beforeTheFactoryIsBuilt.run();
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(
                    MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
            factory.setPackagesToScan("org.danteplanner.backend");
            factory.setJpaVendorAdapter(context.getBean(HibernateJpaVendorAdapter.class));
            factory.setJpaPropertyMap(Map.of(
                    MappingSettings.PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategySnakeCaseImpl.class.getName()));
            return factory;
        });
        if (configurations.length > 0) {
            context.register(configurations);
        }
        context.refresh();
        return context;
    }

    private PlannerComment commentSavedThrough(AnnotationConfigApplicationContext context) {
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        PlannerComment comment = new PlannerComment(planner.id(), owner.getId(), null, 0);
        factory.runInTransaction(manager -> manager.persist(comment));
        return commentRepository.findById(comment.getId()).orElseThrow();
    }

    private Instant lastModifiedAt(UUID plannerId) {
        return plannerRepository.findAggregate(plannerId).orElseThrow().content().lastModifiedAt();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SettableClockConfig {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }
    }

    static final class SettableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(T);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
