package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HangTest {

    private static final Hang.Bound SHORT = new Hang.Bound(Duration.ofMillis(50));

    @Test
    void guard_WhenRead_IsThirtySeconds() {
        assertThat(Hang.GUARD).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void awaitLatch_WhenAlreadyCountedDown_Returns() {
        CountDownLatch latch = new CountDownLatch(0);

        Hang.await(latch, "the open latch");

        assertThat(latch.getCount()).isZero();
    }

    @Test
    void awaitLatch_WhenNeverCountedDown_FailsNamingWhatItWaitedFor() {
        assertThatThrownBy(() -> SHORT.await(new CountDownLatch(1), "the closed latch"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: the closed latch did not complete within 50ms");
    }

    @Test
    void awaitBarrier_WhenItIsTheOnlyParty_Returns() {
        CyclicBarrier barrier = new CyclicBarrier(1);

        Hang.await(barrier, "the one-party barrier");

        assertThat(barrier.isBroken()).isFalse();
    }

    @Test
    void awaitBarrier_WhenTheOtherPartyNeverArrives_FailsNamingWhatItWaitedFor() {
        assertThatThrownBy(() -> SHORT.await(new CyclicBarrier(2), "the two-party barrier"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: the two-party barrier did not complete within 50ms");
    }

    @Test
    void awaitBarrier_WhenInterrupted_FailsAndRestoresTheInterruptFlag() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> SHORT.await(barrier, "the interrupted barrier"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: interrupted while waiting for the interrupted barrier");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void awaitBarrier_WhenAnotherPartyBrokeIt_FailsNamingWhatItWaitedFor() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> SHORT.await(barrier, "the leaving party"))
                .hasMessage("hang: interrupted while waiting for the leaving party");
        assertThat(Thread.interrupted()).isTrue();

        assertThatThrownBy(() -> SHORT.await(barrier, "the broken barrier"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: the broken barrier broke: another party left the barrier");
    }

    @Test
    void get_WhenAlreadyCompleted_ReturnsTheValue() throws Exception {
        assertThat(Hang.get(CompletableFuture.completedFuture("done"), "the completed future")).isEqualTo("done");
    }

    @Test
    void get_WhenNeverCompleted_FailsNamingWhatItWaitedFor() {
        assertThatThrownBy(() -> SHORT.get(new CompletableFuture<>(), "the pending future"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: the pending future did not complete within 50ms");
    }

    @Test
    void terminated_WhenTheExecutorAlreadyTerminated_Returns() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();

        Hang.terminated(executor, "the idle executor");

        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void terminated_WhenATaskNeverEnds_FailsNamingWhatItWaitedFor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch never = new CountDownLatch(1);
        try {
            Future<Object> stuck = executor.submit(() -> {
                never.await();
                return null;
            });
            executor.shutdown();

            assertThatThrownBy(() -> SHORT.terminated(executor, "the stuck executor"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("hang: the stuck executor did not complete within 50ms");
            assertThat(stuck).isNotDone();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void until_WhenTheConditionAlreadyHolds_ReturnsAfterOneEvaluation() {
        int[] evaluations = {0};

        Hang.until(() -> ++evaluations[0] > 0, "the holding condition");

        assertThat(evaluations[0]).isEqualTo(1);
    }

    @Test
    void until_WhenTheConditionHoldsLateInTheLimit_Returns() {
        Duration limit = Duration.ofSeconds(5);
        long holdsAt = System.nanoTime() + limit.multipliedBy(9).dividedBy(10).toNanos();

        new Hang.Bound(limit).until(() -> System.nanoTime() - holdsAt >= 0, "the late condition");

        assertThat(System.nanoTime() - holdsAt).isNotNegative();
    }

    @Test
    void until_WhenTheConditionNeverHolds_FailsNamingWhatItWaitedFor() {
        assertThatThrownBy(() -> SHORT.until(() -> false, "the false condition"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: the false condition did not complete within 50ms");
    }
}
