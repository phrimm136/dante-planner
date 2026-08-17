package org.danteplanner.backend.integration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lock-order seam of the view flush: two pods buffer the same planners in the order their views
 * arrived, so the batches they drain name the same counter rows in opposite orders. The counter
 * row stays X-locked for the length of the flush transaction, which is what makes the ordering a
 * deadlock question rather than a contention one.
 *
 * <p>Each pod is a recorder of its own — the buffer is per instance, and one shared bean would
 * hand both threads the same batch in the same order, which is the case that never deadlocked.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerViewLockOrderIT extends SharedMySqlContainerSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 20);
    private static final int ROUNDS = 20;
    private static final int PODS = 2;
    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long JOIN_TIMEOUT_MS = 60_000;

    @Autowired
    private PlannerViewRepository plannerViewRepository;

    @Autowired
    private PlannerStatsRepository plannerStatsRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("concurrent flushes over the same two planners never deadlock on the counter rows")
    void flush_WhenConcurrentFlushesShareTwoPlanners_AdvancesEveryCounter() throws InterruptedException {
        User owner = TestDataFactory.createTestUser(userRepository, "lock-order-owner@example.com");
        List<UUID> planners = List.of(
                TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId(),
                TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId());

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier eachRound = new CyclicBarrier(PODS);
        Thread forward = new Thread(pod(planners, "forward-viewer-", eachRound, failures), "forward-pod");
        Thread reverse = new Thread(pod(planners.reversed(), "reverse-viewer-", eachRound, failures), "reverse-pod");

        forward.setDaemon(true);
        reverse.setDaemon(true);
        forward.start();
        reverse.start();
        forward.join(JOIN_TIMEOUT_MS);
        reverse.join(JOIN_TIMEOUT_MS);

        assertThat(failures)
                .as("a flush that took the counter rows in the other pod's order loses the deadlock "
                        + "and rolls its whole batch back")
                .isEmpty();
        assertThat(forward.isAlive() || reverse.isAlive())
                .as("both pods finished their rounds")
                .isFalse();
        planners.forEach(plannerId -> assertThat(viewCount(plannerId))
                .as("every distinct viewer of %s counts once", plannerId)
                .isEqualTo(PODS * ROUNDS));
    }

    /**
     * One pod: its own buffer, its own transaction per round, and a viewer hash per round so every
     * round inserts new view rows and therefore touches every counter row.
     *
     * @param plannerIds  the planners to buffer, in this pod's order
     * @param viewerPrefix a prefix unique to this pod
     * @param eachRound   the barrier holding the pods on the same round
     * @param failures    where a round's failure is recorded
     * @return the pod's body
     */
    private Runnable pod(List<UUID> plannerIds, String viewerPrefix, CyclicBarrier eachRound,
            List<Throwable> failures) {
        return () -> {
            PlannerViewRecorder recorder =
                    new PlannerViewRecorder(plannerViewRepository, plannerStatsRepository);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            for (int round = 0; round < ROUNDS; round++) {
                String viewerHash = viewerPrefix + round;
                plannerIds.forEach(plannerId -> recorder.record(plannerId, viewerHash, DAY));
                try {
                    eachRound.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    transaction.executeWithoutResult(status -> recorder.flush());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add(e);
                    return;
                } catch (BrokenBarrierException | TimeoutException | RuntimeException e) {
                    failures.add(e);
                    return;
                }
            }
        };
    }

    private int viewCount(UUID plannerId) {
        return plannerStatsRepository.findById(plannerId).map(PlannerStats::getViewCount).orElse(0);
    }
}
