package org.danteplanner.backend.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionTemplate;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PlannerCatalogRemovalConcurrencyIT extends ExclusiveDatabaseSupport {

    private static final int ROUNDS = 150;
    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long JOIN_TIMEOUT_MS = 60_000;
    private static final int MIN_OVERLAPPING_ROUNDS = ROUNDS / 2;
    private static final ThreadLocal<Span> SPAN = new ThreadLocal<>();

    @Autowired private UserRepository userRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private PlannerContentRepository contentRepository;
    @Autowired private PlannerStatsRepository statsRepository;
    @Autowired private PlannerCatalogRepository catalogRepository;
    @Autowired private PlannerCatalogService catalogService;
    @Autowired private PlannerPublishingService publishingService;
    @Autowired private PlannerCommandService commandService;
    @Autowired private PlannerModerationService moderationService;
    @Autowired private UserAccountLifecycleService lifecycleService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private User moderator;
    private String raceName;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        raceName = testInfo.getTestMethod().orElseThrow().getName();
        jdbc = new JdbcTemplate(dataSource);
        emptyPlannerTables(jdbc);
        tx = new TransactionTemplate(transactionManager);
        moderator = TestDataFactory.createModerator(userRepository, "removal-moderator@example.com");
    }

    @Test
    void unpublish_WhenTheOwnersAccountIsDeactivatedAtOnce_BothSucceedAndTheRowIsGone() throws Exception {
        race(this::unpublish, round -> catalogService.accountDeactivated(round.owner().getId()),
                this::catalogRowGone);
    }

    @Test
    void unpublish_WhenTheOwnersAccountIsDeletedAtOnce_BothSucceedAndTheRowIsGone() throws Exception {
        race(this::unpublish, this::deleteAccount, this::catalogRowGone);
    }

    @Test
    void unpublish_WhenAModeratorTakesThePlannerDownAtOnce_BothSucceedAndTheTakedownIsRecorded()
            throws Exception {
        race(this::unpublish, this::takeDown, round -> catalogRowGone(round) && takedownRecorded(round));
    }

    @Test
    void unpublish_WhenTheOwnerUnpublishesTwiceAtOnce_BothSucceed() throws Exception {
        race(this::unpublish, this::unpublish, this::catalogRowGone);
    }

    @Test
    void deletePlanner_WhenAModeratorTakesThePlannerDownAtOnce_ThePlannerIsDeletedAndTheTakedownRecordedOrRefused()
            throws Exception {
        race(this::deletePlanner, this::takeDown, this::takedownFoundThePlannerDeleted,
                outcome -> catalogRowGone(outcome.round()) && plannerDeleted(outcome.round())
                        && (outcome.second() != null || takedownRecorded(outcome.round())));
    }

    @Test
    void deletePlanner_WhenTheOwnersAccountIsDeletedAtOnce_BothSucceedAndTheRowIsGone() throws Exception {
        race(this::deletePlanner, this::deleteAccount,
                round -> catalogRowGone(round) && plannerDeleted(round) && accountDeleted(round));
    }

    @Test
    void takeDown_WhenTheOwnersAccountIsDeletedAtOnce_BothSucceedAndTheTakedownIsRecorded() throws Exception {
        race(this::takeDown, this::deleteAccount,
                round -> catalogRowGone(round) && takedownRecorded(round) && accountDeleted(round));
    }

    @Test
    void remove_WhenTheRowIsAlreadyAbsent_DoesNotThrow() {
        Round round = seed();
        catalogService.remove(round.plannerId());

        assertThatCode(() -> catalogService.remove(round.plannerId())).doesNotThrowAnyException();
        assertThat(catalogRowGone(round)).isTrue();
    }

    @Test
    void remove_WhenTheCallerRollsBack_KeepsTheRow() {
        Round round = seed();

        tx.executeWithoutResult(status -> {
            catalogService.remove(round.plannerId());
            assertThat(catalogRowGone(round)).as("removed inside the caller's transaction").isTrue();
            status.setRollbackOnly();
        });

        assertThat(catalogRepository.existsById(round.plannerId())).isTrue();
    }

    private record Round(User owner, UUID plannerId) {
    }

    private record Outcome(Round round, Throwable first, Throwable second) {
    }

    private void unpublish(Round round) {
        publishingService.unpublish(round.owner().getId(), round.plannerId());
    }

    private void deletePlanner(Round round) {
        commandService.deletePlanner(round.owner().getId(), round.plannerId());
    }

    private void deleteAccount(Round round) {
        lifecycleService.deleteAccount(round.owner().getId());
    }

    private void takeDown(Round round) {
        moderationService.deletePlanner(moderator.getId(), round.plannerId(), "removal race");
    }

    private boolean catalogRowGone(Round round) {
        return !catalogRepository.existsById(round.plannerId());
    }

    private boolean takedownRecorded(Round round) {
        return takedownRecords(round) == 1;
    }

    private int takedownRecords(Round round) {
        Integer recorded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_actions WHERE target_uuid = ? AND action_type = ?",
                Integer.class, round.plannerId().toString(), ModerationActionType.DELETE_PLANNER.name());
        return recorded != null ? recorded : 0;
    }

    private boolean plannerDeleted(Round round) {
        return contentRepository.findById(round.plannerId()).map(PlannerContent::isDeleted).orElse(false);
    }

    private boolean accountDeleted(Round round) {
        return userRepository.findById(round.owner().getId()).map(User::isDeleted).orElse(false);
    }

    private boolean takedownFoundThePlannerDeleted(Outcome outcome) {
        return outcome.second() instanceof PlannerNotFoundException
                && plannerDeleted(outcome.round())
                && takedownRecords(outcome.round()) == 0;
    }

    private Round seed() {
        User owner = TestDataFactory.createTestUser(userRepository, "removal-owner@example.com");
        UUID plannerId = TestDataFactory.planner(owner)
                .savePublished(plannerRepository, statsRepository, catalogService)
                .getId();
        return new Round(owner, plannerId);
    }

    private void race(Consumer<Round> first, Consumer<Round> second, Predicate<Round> settled) throws Exception {
        race(first, second, outcome -> false, outcome -> settled.test(outcome.round()));
    }

    private void race(Consumer<Round> first, Consumer<Round> second, Predicate<Outcome> secondExcused,
            Predicate<Outcome> settled) throws Exception {
        List<Throwable> firstFailures = new ArrayList<>();
        List<Throwable> secondFailures = new ArrayList<>();
        List<Integer> unsettledRounds = new ArrayList<>();
        int overlappingRounds = 0;
        int excusedRounds = 0;
        ConfigurableTransactionManager manager = (ConfigurableTransactionManager) transactionManager;
        Collection<TransactionExecutionListener> listeners = manager.getTransactionExecutionListeners();
        List<TransactionExecutionListener> withSpans = new ArrayList<>(listeners);
        withSpans.add(new SpanListener());
        manager.setTransactionExecutionListeners(withSpans);
        try {
            for (int index = 0; index < ROUNDS; index++) {
                Round round = seed();
                CyclicBarrier start = new CyclicBarrier(2);
                Span firstSpan = new Span();
                Span secondSpan = new Span();
                AtomicReference<Throwable> firstFailure = new AtomicReference<>();
                AtomicReference<Throwable> secondFailure = new AtomicReference<>();
                Thread one = thread("first", start, firstSpan, firstFailure, () -> first.accept(round));
                Thread two = thread("second", start, secondSpan, secondFailure, () -> second.accept(round));
                one.start();
                two.start();
                one.join(JOIN_TIMEOUT_MS);
                two.join(JOIN_TIMEOUT_MS);
                assertThat(one.isAlive() || two.isAlive()).as("round %d finished", index).isFalse();
                if (firstSpan.overlaps(secondSpan)) {
                    overlappingRounds++;
                }
                Outcome outcome = new Outcome(round, firstFailure.get(), secondFailure.get());
                if (outcome.first() != null) {
                    firstFailures.add(outcome.first());
                }
                if (outcome.second() != null) {
                    if (secondExcused.test(outcome)) {
                        excusedRounds++;
                    } else {
                        secondFailures.add(outcome.second());
                    }
                }
                if (!settled.test(outcome)) {
                    unsettledRounds.add(index);
                }
            }
        } finally {
            manager.setTransactionExecutionListeners(listeners);
        }
        System.out.println("catalog removal race " + raceName + ": " + overlappingRounds + " of " + ROUNDS
                + " rounds overlapped, " + excusedRounds + " second removers found their target gone");
        int overlapped = overlappingRounds;
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(tally(firstFailures)).as("first remover failures of %d rounds", ROUNDS).isEmpty();
            softly.assertThat(tally(secondFailures)).as("second remover failures of %d rounds", ROUNDS).isEmpty();
            softly.assertThat(unsettledRounds).as("rounds whose end state is wrong").isEmpty();
            softly.assertThat(overlapped).as("rounds whose transactions overlapped").isGreaterThanOrEqualTo(
                    MIN_OVERLAPPING_ROUNDS);
        });
    }

    private static Map<String, Long> tally(List<Throwable> failures) {
        return failures.stream().collect(Collectors.groupingBy(
                failure -> failure.getClass().getName(), Collectors.counting()));
    }

    private Thread thread(String name, CyclicBarrier start, Span span, AtomicReference<Throwable> failure,
            Runnable body) {
        Thread thread = new Thread(() -> {
            SPAN.set(span);
            try {
                start.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                SPAN.remove();
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Span {

        private long begunAt = -1;
        private long endedAt = -1;
        private int depth;

        void begun() {
            if (depth++ == 0 && begunAt < 0) {
                begunAt = System.nanoTime();
            }
        }

        void ended() {
            if (--depth == 0 && endedAt < 0) {
                endedAt = System.nanoTime();
            }
        }

        boolean overlaps(Span other) {
            return endedAt >= 0 && other.endedAt >= 0
                    && Math.max(begunAt, other.begunAt) < Math.min(endedAt, other.endedAt);
        }
    }

    private static final class SpanListener implements TransactionExecutionListener {

        @Override
        public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
            Span span = SPAN.get();
            if (span != null && beginFailure == null) {
                span.begun();
            }
        }

        @Override
        public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
            Span span = SPAN.get();
            if (span != null) {
                span.ended();
            }
        }

        @Override
        public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
            Span span = SPAN.get();
            if (span != null) {
                span.ended();
            }
        }
    }
}
