package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.pollinterval.FibonacciPollInterval;
import org.awaitility.pollinterval.PollInterval;

public final class Hang {

    public static final Duration GUARD = Duration.ofSeconds(30);

    private static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(1);

    private static final Bound DEFAULT = new Bound(GUARD);

    private Hang() {
    }

    public static void await(CountDownLatch latch, String what) {
        DEFAULT.await(latch, what);
    }

    public static void await(CyclicBarrier barrier, String what) {
        DEFAULT.await(barrier, what);
    }

    public static <T> T get(Future<T> future, String what) throws ExecutionException {
        return DEFAULT.get(future, what);
    }

    public static void terminated(ExecutorService executor, String what) {
        DEFAULT.terminated(executor, what);
    }

    public static void until(BooleanSupplier condition, String what) {
        DEFAULT.until(condition, what);
    }

    static final class Bound {

        private final Duration limit;

        Bound(Duration limit) {
            this.limit = limit;
        }

        void await(CountDownLatch latch, String what) {
            try {
                if (!latch.await(limit.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw expired(what, null);
                }
            } catch (InterruptedException e) {
                throw interrupted(what, e);
            }
        }

        void await(CyclicBarrier barrier, String what) {
            try {
                barrier.await(limit.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw expired(what, e);
            } catch (BrokenBarrierException e) {
                throw new AssertionError("hang: " + what + " broke: another party left the barrier", e);
            } catch (InterruptedException e) {
                throw interrupted(what, e);
            }
        }

        <T> T get(Future<T> future, String what) throws ExecutionException {
            try {
                return future.get(limit.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw expired(what, e);
            } catch (InterruptedException e) {
                throw interrupted(what, e);
            }
        }

        void terminated(ExecutorService executor, String what) {
            try {
                if (!executor.awaitTermination(limit.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw expired(what, null);
                }
            } catch (InterruptedException e) {
                throw interrupted(what, e);
            }
        }

        void until(BooleanSupplier condition, String what) {
            try {
                Awaitility.await(what).pollInSameThread().pollDelay(Duration.ZERO)
                        .pollInterval(cappedFibonacci()).atMost(limit)
                        .until(condition::getAsBoolean);
            } catch (ConditionTimeoutException e) {
                throw expired(what, e);
            }
        }

        private static PollInterval cappedFibonacci() {
            PollInterval fibonacci = FibonacciPollInterval.fibonacci(TimeUnit.MILLISECONDS);
            return (pollCount, previous) -> {
                Duration next = fibonacci.next(pollCount, previous);
                return next.compareTo(MAX_POLL_INTERVAL) > 0 ? MAX_POLL_INTERVAL : next;
            };
        }

        private AssertionError expired(String what, Throwable cause) {
            return new AssertionError("hang: " + what + " did not complete within " + seconds(), cause);
        }

        private String seconds() {
            return limit.toMillis() % 1000 == 0 ? limit.toSeconds() + "s" : limit.toMillis() + "ms";
        }

        private static AssertionError interrupted(String what, InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AssertionError("hang: interrupted while waiting for " + what, e);
        }
    }
}
