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

import org.danteplanner.backend.comment.entity.CommentVoteType;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.entity.PlannerCommentVote;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.config.JpaAuditingConfig;
import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
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
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
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

    @Autowired
    private SettableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Planner planner;

    @BeforeEach
    void setUp() {
        clock.set(T);
        owner = TestDataFactory.createTestUser(userRepository, "audited@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @Test
    void newComment_WhenTheClockIsFixed_IsStampedWithItsInstant() {
        Long id = transactionTemplate.execute(status -> commentRepository.insert(
                new PlannerComment(planner.getId(), owner.getId(), "stamped", null, 0)).getId());

        PlannerComment stored = commentRepository.findById(id).orElseThrow();

        assertThat(stored.getCreatedAt()).isEqualTo(T);
        assertThat(stored.getPublicId()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("auditedRows")
    void newRow_WhenTheClockIsFixed_IsStampedWithItsInstant(BiFunction<Long, Planner, Object> row) {
        Object unsaved = row.apply(owner.getId(), planner);
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(unsaved));

        assertThat(storedCreatedAt(unsaved)).isEqualTo(T);
    }

    @Test
    void newCommentVote_WhenTheClockIsFixed_IsStampedWithItsInstant() {
        PlannerComment comment = commentRepository.save(
                new PlannerComment(planner.getId(), owner.getId(), "voted", null, 0));
        PlannerCommentVote vote = new PlannerCommentVote(comment.getId(), owner.getId(), CommentVoteType.UP);
        PlannerCommentReport report = new PlannerCommentReport(comment.getId(), owner.getId(), "SPAM");

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(vote);
            entityManager.persist(report);
        });

        assertThat(storedCreatedAt(vote)).isEqualTo(T);
        assertThat(storedCreatedAt(report)).isEqualTo(T);
    }

    private static Stream<Named<BiFunction<Long, Planner, Object>>> auditedRows() {
        return Stream.of(
                Named.of("vote", (userId, planner) -> new PlannerVote(userId, planner.getId(), VoteType.UP)),
                Named.of("subscription", (userId, planner) -> new PlannerSubscription(userId, planner.getId())),
                Named.of("view", (userId, planner) -> new PlannerView(planner.getId(),
                        ViewerHashUtil.hashForAuthenticatedUser(userId, planner.getId()), LocalDate.of(2026, 3, 4))),
                Named.of("report", (userId, planner) -> new PlannerReport(userId, planner.getId())),
                Named.of("notification", (userId, planner) -> new Notification(
                        userId, planner.getId().toString(), NotificationType.COMMENT_RECEIVED)),
                Named.of("domain event", (userId, planner) -> dispatchedEvent(planner)),
                Named.of("moderation action", (userId, planner) -> ModerationAction.builder()
                        .actionType(ModerationActionType.TIMEOUT)
                        .actorId(userId)
                        .targetUuid(planner.getId().toString())
                        .targetType(ModerationTargetType.USER)
                        .build()));
    }

    private static DomainEvent dispatchedEvent(Planner planner) {
        DomainEvent event = DomainEvent.of(DomainEventType.PLANNER_PUBLISHED, planner.getId(), "{}");
        event.markDispatched(T);
        return event;
    }

    private Object storedCreatedAt(Object row) {
        Object id = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(row);
        return transactionTemplate.execute(status ->
                ReflectionTestUtils.getField(entityManager.find(row.getClass(), id), "createdAt"));
    }

    @Test
    void newPlanner_WhenCreatedWithItsSiblings_IsStampedOnceAcrossTheAggregate() {
        Planner stored = plannerRepository.findById(planner.getId()).orElseThrow();

        assertThat(stored.getCreatedAt()).isEqualTo(T);
        assertThat(stored.getLastModifiedAt()).isEqualTo(T);
        assertThat(stored.getFirstPublishedAt()).isNotNull();
    }

    @Test
    void newPlanner_WhenTheContentAlreadyCarriesAStamp_TakesTheClockInstead() {
        Planner unsaved = TestDataFactory.planner(owner).build();
        assertThat(unsaved.getLastModifiedAt()).isNotNull().isNotEqualTo(T);

        plannerRepository.save(unsaved);

        assertThat(lastModifiedAt(unsaved.getId())).isEqualTo(T);
    }

    @Test
    void contentRow_WhenNothingChanges_KeepsLastModifiedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                plannerRepository.save(plannerRepository.findById(planner.getId()).orElseThrow()));

        assertThat(lastModifiedAt(planner.getId())).isEqualTo(T);
    }

    @Test
    void contentRow_WhenAFieldChanges_MovesLastModifiedAtAndKeepsCreatedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                plannerRepository.findById(planner.getId()).orElseThrow().getContent().setTitle("Renamed"));

        assertThat(lastModifiedAt(planner.getId())).isEqualTo(LATER);
        assertThat(plannerRepository.findById(planner.getId()).orElseThrow().getCreatedAt()).isEqualTo(T);
    }

    @Test
    void contentRow_WhenOnlyASiblingTableChanges_KeepsLastModifiedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                plannerRepository.findById(planner.getId()).orElseThrow().setOwnerNotificationsEnabled(false));

        Planner stored = plannerRepository.findById(planner.getId()).orElseThrow();
        assertThat(stored.isOwnerNotificationsEnabled()).isFalse();
        assertThat(stored.getLastModifiedAt()).isEqualTo(T);
    }

    @Test
    void userRow_WhenItChanges_MovesUpdatedAtAndKeepsCreatedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(owner.getId()).orElseThrow().setUsernameEpithet("ASTUTE"));

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
    void userRow_WhenNothingChanges_KeepsUpdatedAt() {
        clock.set(LATER);

        transactionTemplate.executeWithoutResult(status ->
                userRepository.save(userRepository.findById(owner.getId()).orElseThrow()));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getUpdatedAt()).isEqualTo(T);
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

    @Test
    void changedRow_WhenTheContextHasNoAuditing_FailsTheSave() {
        PlannerComment stored = commentRepository.save(
                new PlannerComment(planner.getId(), owner.getId(), "unmoved", null, 0));
        try (AnnotationConfigApplicationContext unaudited = jpaContext(SUBJECT_TIME, () -> { })) {
            EntityManagerFactory factory = unaudited.getBean(EntityManagerFactory.class);

            assertThatThrownBy(() -> factory.runInTransaction(manager ->
                    manager.find(PlannerComment.class, stored.getId()).edit("moved", T)))
                    .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                            .isInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasMessageContaining("AuditingHandler"));
        }
        assertThat(commentRepository.findById(stored.getId()).orElseThrow().getContent()).isEqualTo("unmoved");
    }

    @Test
    void newRow_WhenTheEntityHasNoAuditedField_IsRefused() {
        AuditedTimestampListener listener = beanFactory.createBean(AuditedTimestampListener.class);

        assertThatThrownBy(() -> listener.onCreate(PlannerStats.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PlannerStats.class.getName());
    }

    @Test
    void changedRow_WhenTheEntityHasNoAuditedField_IsRefused() {
        AuditedTimestampListener listener = beanFactory.createBean(AuditedTimestampListener.class);

        assertThatThrownBy(() -> listener.onUpdate(PlannerStats.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PlannerStats.class.getName());
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
        PlannerComment comment = new PlannerComment(planner.getId(), owner.getId(), "isolated", null, 0);
        factory.runInTransaction(manager -> manager.persist(comment));
        return commentRepository.findById(comment.getId()).orElseThrow();
    }

    private Instant lastModifiedAt(UUID plannerId) {
        return plannerRepository.findById(plannerId).orElseThrow().getLastModifiedAt();
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
