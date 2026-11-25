package org.danteplanner.backend.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStateRaceIT extends SharedMySqlContainerSupport {

    private static final int ROUNDS = 20;
    private static final int FIRST_TOGGLES = 4;
    private static final int SUBSCRIBES = 8;

    @Autowired private UserRepository userRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private PlannerStatsRepository statsRepository;
    @Autowired private PlannerCatalogRepository catalogRepository;
    @Autowired private PlannerPublishingService publishingService;
    @Autowired private PlannerSubscriptionService subscriptionService;
    @Autowired private PlannerModerationService moderationService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private User owner;
    private User moderator;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "state-race-owner@example.com");
        moderator = TestDataFactory.createModerator(userRepository, "state-race-moderator@example.com");
    }

    @Test
    void publish_WhenRepeatedAcrossAnUnpublish_RecordsOneEventAndKeepsTheFirstStamp() {
        UUID id = draft();

        assertThat(publishingService.publish(owner.getId(), id).published()).isTrue();
        Instant firstStamp = stored(id).getFirstPublishedAt();
        assertThat(publishingService.publish(owner.getId(), id).published()).isTrue();
        assertAgrees(id, true);

        assertThat(publishingService.unpublish(owner.getId(), id).published()).isFalse();
        assertThat(publishingService.unpublish(owner.getId(), id).published()).isFalse();
        assertAgrees(id, false);

        assertThat(publishingService.publish(owner.getId(), id).published()).isTrue();
        assertAgrees(id, true);
        assertThat(stored(id).getFirstPublishedAt()).isNotNull().isEqualTo(firstStamp);
        assertThat(publishedEvents(id)).isEqualTo(1);
    }

    @Test
    void publish_WhenTakenDownAtOnce_IsWithdrawnOrRefusedAndAnnouncedAtMostOnce() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = draft();
            Race race = race(() -> publishingService.publish(owner.getId(), id),
                    () -> moderationService.deletePlanner(moderator.getId(), id, "race"));

            boolean refused = race.first() instanceof PlannerForbiddenException;
            if ((race.first() != null && !refused) || race.second() != null) {
                defects.add(round + ": " + race);
            }
            if (published(id) || catalogued(id) || stored(id).getTakenDownAt() == null) {
                defects.add(round + ": not withdrawn");
            }
            if (publishedEvents(id) != (refused ? 0 : 1)) {
                defects.add(round + ": " + publishedEvents(id) + " events, refused " + refused);
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void publish_WhenUnpublishedAtOnce_LeavesCatalogAndPublicationAgreeing() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = draft();
            Race race = race(() -> publishingService.publish(owner.getId(), id),
                    () -> publishingService.unpublish(owner.getId(), id));

            if (race.failed() || published(id) != catalogued(id) || publishedEvents(id) != 1) {
                defects.add(round + ": " + race + " published " + published(id) + " catalogued " + catalogued(id)
                        + " events " + publishedEvents(id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void publish_WhenPublishedTwiceAtOnce_RecordsOneEvent() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = draft();
            Race race = race(() -> publishingService.publish(owner.getId(), id),
                    () -> publishingService.publish(owner.getId(), id));

            if (race.failed() || !published(id) || !catalogued(id) || publishedEvents(id) != 1) {
                defects.add(round + ": " + race + " events " + publishedEvents(id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void hide_WhenPublishedAtOnce_KeepsBothAndUnhidingAtAnUnpublishKeepsBoth() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = draft();
            Race hiding = race(() -> publishingService.publish(owner.getId(), id), () -> hide(id));
            if (hiding.failed() || !published(id) || !catalogued(id) || !stored(id).isHiddenFromRecommended()) {
                defects.add(round + ": " + hiding + " after hide");
            }

            Race unhiding = race(() -> publishingService.unpublish(owner.getId(), id), () -> unhide(id));
            if (unhiding.failed() || published(id) || catalogued(id) || stored(id).isHiddenFromRecommended()) {
                defects.add(round + ": " + unhiding + " after unhide");
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void publish_WhenTakenDown_IsRefusedAsForbidden() {
        UUID id = draft();
        moderationService.deletePlanner(moderator.getId(), id, "taken down");

        assertRejected(() -> publishingService.publish(owner.getId(), id)).withCode("PLANNER_FORBIDDEN");

        assertAgrees(id, false);
        assertThat(publishedEvents(id)).isZero();
    }

    @Test
    void takeDown_WhenPublishedUnpublishedAndAlreadyTakenDown_WithdrawsOnceAndKeepsTheFirstStamp() {
        UUID listed = draft();
        publishingService.publish(owner.getId(), listed);
        UUID unlisted = draft();

        for (UUID id : List.of(listed, unlisted)) {
            ModeratedPlanner taken = moderationService.deletePlanner(moderator.getId(), id, "first");
            assertThat(taken.published()).isFalse();
            assertAgrees(id, false);
            Instant stamp = stored(id).getTakenDownAt();
            assertThat(stamp).isNotNull();

            assertThat(moderationService.deletePlanner(moderator.getId(), id, "again").published()).isFalse();
            assertThat(stored(id).getTakenDownAt()).isEqualTo(stamp);
        }
    }

    @Test
    void hide_WhenItsTransactionReadThePlannerBeforeATakedown_KeepsTheTakedown() {
        UUID id = draft();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(published(id)).isFalse();
            elsewhere(() -> moderationService.deletePlanner(moderator.getId(), id, "between"));
            hide(id);
        });

        assertThat(stored(id).getTakenDownAt()).isNotNull();
        assertThat(stored(id).isHiddenFromRecommended()).isTrue();
    }

    @Test
    void toggleOwnerNotifications_WhenItsTransactionReadThePlannerBeforeAPublish_KeepsThePublication() {
        UUID id = draft();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(published(id)).isFalse();
            elsewhere(() -> publishingService.publish(owner.getId(), id));
            publishingService.toggleOwnerNotifications(owner.getId(), id, false);
        });

        assertThat(stored(id).isOwnerNotificationsEnabled()).isFalse();
        assertThat(stored(id).getFirstPublishedAt()).isNotNull();
        assertAgrees(id, true);
    }

    @Test
    void toggleSubscription_WhenTwoTogglesMeetOnOneRow_EndsAtTheirParity() throws Exception {
        UUID id = publishedPlanner();
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reader = reader(round);
            subscriptionService.toggleSubscription(reader.getId(), id);

            Race race = race(() -> subscriptionService.toggleSubscription(reader.getId(), id),
                    () -> subscriptionService.toggleSubscription(reader.getId(), id));
            if (race.failed() || !List.of(true).equals(subscriptions(reader, id))) {
                defects.add(round + ": " + race + " " + subscriptions(reader, id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void toggleSubscription_WhenTwoFirstTogglesMeet_InsertsOneRowAndEndsOff() throws Exception {
        UUID id = publishedPlanner();
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reader = reader(round);

            Race race = race(() -> subscriptionService.toggleSubscription(reader.getId(), id),
                    () -> subscriptionService.toggleSubscription(reader.getId(), id));
            if (race.failed() || !List.of(false).equals(subscriptions(reader, id))) {
                defects.add(round + ": " + race + " " + subscriptions(reader, id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void toggleSubscription_WhenFourFirstTogglesMeet_EndsOffWithoutADeadlockOrADuplicate() throws Exception {
        UUID id = publishedPlanner();
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reader = reader(round);

            List<Throwable> thrown = together(FIRST_TOGGLES,
                    () -> subscriptionService.toggleSubscription(reader.getId(), id));
            if (!thrown.isEmpty() || !List.of(false).equals(subscriptions(reader, id))) {
                defects.add(round + ": " + thrown + " " + subscriptions(reader, id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void subscribe_WhenEightMeet_EndsSubscribedOnOneRow() throws Exception {
        UUID id = publishedPlanner();
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reader = reader(round);

            List<Throwable> thrown = together(SUBSCRIBES, () -> subscriptionService.subscribe(reader.getId(), id));
            if (!thrown.isEmpty() || !List.of(true).equals(subscriptions(reader, id))) {
                defects.add(round + ": " + thrown + " " + subscriptions(reader, id));
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void subscribe_WhenUnsubscribedAtOnce_EndsInOneOfTheTwoStatesWithoutAnError() throws Exception {
        UUID id = publishedPlanner();
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reader = reader(round);
            for (String start : List.of("no row", "a row")) {
                Race race = race(() -> subscriptionService.subscribe(reader.getId(), id),
                        () -> subscriptionService.unsubscribe(reader.getId(), id));
                if (race.failed() || subscriptions(reader, id).size() != 1) {
                    defects.add(round + " from " + start + ": " + race + " " + subscriptions(reader, id));
                }
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void toggleSubscription_WhenThePlannerIsMissing_AnswersItAsAbsentAndInsertsNothing() {
        UUID missing = UUID.randomUUID();

        assertRejected(() -> subscriptionService.toggleSubscription(owner.getId(), missing))
                .withCode("PLANNER_NOT_FOUND");

        assertThat(subscriptions(owner, missing)).isEmpty();
    }

    @Test
    void publish_WhenTheOwnerOptedOutBeforeARepublish_KeepsTheOptOut() {
        UUID id = draft();
        publishingService.publish(owner.getId(), id);
        subscriptionService.toggleSubscription(owner.getId(), id);
        publishingService.unpublish(owner.getId(), id);

        publishingService.publish(owner.getId(), id);

        assertThat(subscriptions(owner, id)).containsExactly(false);
    }

    private record Race(Throwable first, Throwable second) {

        boolean failed() {
            return first != null || second != null;
        }
    }

    private static Race race(Runnable first, Runnable second) {
        CyclicBarrier start = new CyclicBarrier(2);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread one = started(first, start, firstFailure);
        Thread two = started(second, start, secondFailure);
        Hang.ended(one, "the first racer");
        Hang.ended(two, "the second racer");
        return new Race(firstFailure.get(), secondFailure.get());
    }

    private static List<Throwable> together(int parties, Runnable body) {
        CyclicBarrier start = new CyclicBarrier(parties);
        List<AtomicReference<Throwable>> failures = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int party = 0; party < parties; party++) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            failures.add(failure);
            threads.add(started(body, start, failure));
        }
        threads.forEach(thread -> Hang.ended(thread, "a racer"));
        return failures.stream().map(AtomicReference::get).filter(Objects::nonNull).toList();
    }

    private static Thread started(Runnable body, CyclicBarrier start, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                Hang.await(start, "both racers reaching the barrier");
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void elsewhere(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Hang.ended(started(body, new CyclicBarrier(1), failure), "the concurrent write");
        assertThat(failure.get()).isNull();
    }

    private void hide(UUID id) {
        moderationService.hideFromRecommended(id, moderator.getId(), new HidePlannerRequest("off-list"));
    }

    private void unhide(UUID id) {
        moderationService.unhideFromRecommended(id, moderator.getId());
    }

    private User reader(int round) {
        return TestDataFactory.createTestUser(userRepository, "state-race-reader" + round + "@example.com");
    }

    private UUID draft() {
        UUID id = TestDataFactory.createTestPlanner(plannerRepository, owner, false).getId();
        statsRepository.save(PlannerStats.builder().plannerId(id).build());
        return id;
    }

    private UUID publishedPlanner() {
        UUID id = draft();
        publishingService.publish(owner.getId(), id);
        return id;
    }

    private Planner stored(UUID id) {
        return plannerRepository.findAggregate(id).orElseThrow();
    }

    private boolean published(UUID id) {
        return stored(id).isPublished();
    }

    private boolean catalogued(UUID id) {
        return catalogRepository.existsById(id);
    }

    private void assertAgrees(UUID id, boolean visible) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(published(id)).as("published").isEqualTo(visible);
            softly.assertThat(catalogued(id)).as("catalog row").isEqualTo(visible);
        });
    }

    private int publishedEvents(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = ?",
                Integer.class, id.toString(), DomainEventType.PLANNER_PUBLISHED.name());
    }

    private List<Boolean> subscriptions(User user, UUID id) {
        return jdbc.queryForList(
                "SELECT enabled FROM planner_subscriptions WHERE user_id = ? AND planner_id = UUID_TO_BIN(?)",
                Boolean.class, user.getId(), id.toString());
    }
}
