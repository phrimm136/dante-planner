package org.danteplanner.backend.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
import org.danteplanner.backend.support.TransactionSpans;
import org.danteplanner.backend.support.TransactionSpans.Span;
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
    private static final long LOCK_WAIT_POLL_MS = 10;
    private static final String CONTENT_LOCK_WAITERS = """
            SELECT COUNT(*) FROM information_schema.processlist
            WHERE db = DATABASE() AND state = 'statistics'
              AND info LIKE '%FROM planner_content WHERE planner_id = % FOR UPDATE'
            """;
    private static final String ACTIVE_STATEMENTS = """
            SELECT id, command, time, state, info FROM information_schema.processlist
            WHERE db = DATABASE() AND command <> 'Sleep'
            """;

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
        List<Outcome> outcomes = race(this::seed, this::deletePlanner, this::takeDown, true,
                this::takedownFoundThePlannerDeleted,
                outcome -> catalogRowGone(outcome.round()) && plannerDeleted(outcome.round())
                        && (outcome.second() != null || takedownRecorded(outcome.round())));

        List<Integer> mismatched = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            boolean deleteLed = firstLeads(index);
            boolean deleteWon = takedownFoundThePlannerDeleted(outcomes.get(index));
            boolean takedownWon = outcomes.get(index).second() == null && takedownRecorded(outcomes.get(index).round());
            if (deleteWon != deleteLed || takedownWon == deleteLed) {
                mismatched.add(index);
            }
        }
        System.out.println("catalog removal race " + raceName + ": delete led " + (outcomes.size() + 1) / 2
                + " rounds, takedown led " + outcomes.size() / 2 + ", rounds whose winner was not the leader "
                + mismatched);
        assertThat(mismatched).as("rounds whose winner was not the transaction first in the lock queue").isEmpty();
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
    void takeDown_WhenTheDeletedOwnersAccountIsReactivatedAtOnce_BothSucceedAndTheTakedownIsRecorded()
            throws Exception {
        race(this::seedWithDeletedOwner, this::reactivateAccount, this::takeDown, false, outcome -> false,
                outcome -> catalogRowGone(outcome.round()) && takedownRecorded(outcome.round())
                        && !accountDeleted(outcome.round()));
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

    private void reactivateAccount(Round round) {
        lifecycleService.reactivateAccount(round.owner().getId());
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

    private Round seedWithDeletedOwner() {
        Round round = seed();
        lifecycleService.deleteAccount(round.owner().getId());
        return round;
    }

    private void race(Consumer<Round> first, Consumer<Round> second, Predicate<Round> settled) throws Exception {
        race(first, second, outcome -> false, outcome -> settled.test(outcome.round()));
    }

    private void race(Consumer<Round> first, Consumer<Round> second, Predicate<Outcome> secondExcused,
            Predicate<Outcome> settled) throws Exception {
        race(this::seed, first, second, false, secondExcused, settled);
    }

    private List<Outcome> race(Supplier<Round> seeder, Consumer<Round> first, Consumer<Round> second,
            boolean orderedByLockQueue, Predicate<Outcome> secondExcused, Predicate<Outcome> settled)
            throws Exception {
        List<Outcome> outcomes = new ArrayList<>();
        List<Throwable> firstFailures = new ArrayList<>();
        List<Throwable> secondFailures = new ArrayList<>();
        List<Integer> unsettledRounds = new ArrayList<>();
        int overlappingRounds = 0;
        int excusedRounds = 0;
        try (TransactionSpans.Installed unused = TransactionSpans.install(transactionManager)) {
            for (int index = 0; index < ROUNDS; index++) {
                Round round = seeder.get();
                CyclicBarrier start = new CyclicBarrier(orderedByLockQueue ? 1 : 2);
                Span firstSpan = new Span();
                Span secondSpan = new Span();
                AtomicReference<Throwable> firstFailure = new AtomicReference<>();
                AtomicReference<Throwable> secondFailure = new AtomicReference<>();
                Thread one = thread("first", start, firstSpan, firstFailure, () -> first.accept(round));
                Thread two = thread("second", start, secondSpan, secondFailure, () -> second.accept(round));
                if (orderedByLockQueue) {
                    startInLockQueueOrder(round, index, firstLeads(index) ? one : two, firstLeads(index) ? two : one,
                            firstLeads(index) ? firstFailure : secondFailure,
                            firstLeads(index) ? secondFailure : firstFailure);
                } else {
                    one.start();
                    two.start();
                }
                one.join(JOIN_TIMEOUT_MS);
                two.join(JOIN_TIMEOUT_MS);
                assertThat(one.isAlive() || two.isAlive()).as("round %d finished", index).isFalse();
                if (firstSpan.overlaps(secondSpan)) {
                    overlappingRounds++;
                }
                Outcome outcome = new Outcome(round, firstFailure.get(), secondFailure.get());
                outcomes.add(outcome);
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
        return outcomes;
    }

    private static boolean firstLeads(int round) {
        return round % 2 == 0;
    }

    private void startInLockQueueOrder(Round round, int index, Thread leader, Thread follower,
            AtomicReference<Throwable> leaderFailure, AtomicReference<Throwable> followerFailure) throws Exception {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT planner_id FROM planner_content WHERE planner_id = UUID_TO_BIN(?) FOR UPDATE")) {
                lock.setString(1, round.plannerId().toString());
                lock.executeQuery().close();
            }
            leader.start();
            awaitLockWaits(1, index, leader, leaderFailure);
            follower.start();
            awaitLockWaits(2, index, follower, followerFailure);
            holder.rollback();
        }
    }

    private void awaitLockWaits(int expected, int index, Thread started, AtomicReference<Throwable> failure)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(BARRIER_TIMEOUT_SECONDS));
        while (contentLockWaiters() < expected) {
            assertThat(started.isAlive())
                    .as("round %d: %s ended before it queued on the held content row (failure %s)",
                            index, started.getName(), failure.get())
                    .isTrue();
            assertThat(Instant.now())
                    .as("round %d: %d statements waiting on the held content row, saw %s",
                            index, expected, jdbc.queryForList(ACTIVE_STATEMENTS))
                    .isBefore(deadline);
            TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_POLL_MS);
        }
    }

    private int contentLockWaiters() {
        Integer waiting = jdbc.queryForObject(CONTENT_LOCK_WAITERS, Integer.class);
        return waiting != null ? waiting : 0;
    }

    private static Map<String, Long> tally(List<Throwable> failures) {
        return failures.stream().collect(Collectors.groupingBy(
                failure -> failure.getClass().getName(), Collectors.counting()));
    }

    private Thread thread(String name, CyclicBarrier start, Span span, AtomicReference<Throwable> failure,
            Runnable body) {
        Thread thread = new Thread(() -> {
            try {
                start.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                TransactionSpans.within(span, body);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }
}
