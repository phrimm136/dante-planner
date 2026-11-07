package org.danteplanner.backend.planner.scheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.boot.web.server.context.WebServerApplicationContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.SoftAssertions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.redis.testcontainers.RedisContainer;

import org.danteplanner.backend.planner.config.ViewFlushSchedulerConfig;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService.BackfillPage;
import org.danteplanner.backend.shared.config.ShedLockConfig;
import org.danteplanner.backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("containerized")
class KeywordBackfillShutdownRaceIT {

    private static final String LOCK_KEY = "job-lock:default:backfillPlannerKeywords";
    private static final String SCHEDULER_THREAD = "scheduling-1";
    /** Long enough for close() to pass the scheduler's stop phase first, far below the phase timeout. */
    private static final long FIRE_DELAY_MS = 500;
    /** Boot's spring.lifecycle.timeout-per-shutdown-phase default, which prod does not override. */
    private static final long PHASE_TIMEOUT_MS = 30_000;
    /** Between the scheduler's phase and LettuceConnectionFactory's, where other lifecycle beans stop in prod. */
    private static final int GATE_PHASE = 1;

    private static RedisContainer redis;

    @BeforeAll
    static void startRedis() {
        redis = new RedisContainer("redis:7-alpine");
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @Test
    void contextClose_WhenTheOneShotBackfillFiresAfterTheSchedulerStopped_ReleasesItsLock() throws Exception {
        Probe probe = new Probe(false);
        AnnotationConfigApplicationContext context = start(probe, FIRE_DELAY_MS);

        List<ILoggingEvent> errors;
        long closeMs;
        try (LogCapture capture = LogCapture.onThread(SCHEDULER_THREAD, loggingErrorHandler())) {
            long started = System.nanoTime();
            context.close();
            closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (probe.worker.get() != null) {
                probe.worker.get().join();
            }
            errors = capture.at(Level.ERROR);
        }
        Long leftoverTtlSeconds = lockTtlSeconds();

        String path = probe.worker.get() == null ? "the backfill never fired during close"
                : "the backfill fired during close; close took " + closeMs + " ms against a " + PHASE_TIMEOUT_MS
                        + " ms phase timeout";
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(probe.worker.get()).as("thread that ran the backfill after close began").isNull();
            soft.assertThat(errors.stream().map(KeywordBackfillShutdownRaceIT::describe).toList())
                    .as("errors logged by the scheduler's error handler (%s)", path).isEmpty();
            soft.assertThat(leftoverTtlSeconds).as("TTL in seconds of %s after close (-2 = absent)", LOCK_KEY)
                    .isEqualTo(-2L);
        });
    }

    @Test
    void contextClose_WhenTheBackfillIsMidPage_StopsAfterThatPageAndReleasesItsLock() throws Exception {
        Probe probe = new Probe(true);
        AnnotationConfigApplicationContext context = start(probe, 0);
        probe.lockAcquired.await();

        List<ILoggingEvent> errors;
        long closeMs;
        try (LogCapture capture = LogCapture.onThread(SCHEDULER_THREAD, loggingErrorHandler())) {
            long started = System.nanoTime();
            context.close();
            closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            probe.worker.get().join();
            errors = capture.at(Level.ERROR);
        }
        Long leftoverTtlSeconds = lockTtlSeconds();

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(probe.pages.get()).as("pages the backfill requested").isEqualTo(1);
            soft.assertThat(errors.stream().map(KeywordBackfillShutdownRaceIT::describe).toList())
                    .as("errors logged by the scheduler's error handler").isEmpty();
            soft.assertThat(leftoverTtlSeconds).as("TTL in seconds of %s after close (-2 = absent)", LOCK_KEY)
                    .isEqualTo(-2L);
            soft.assertThat(closeMs).as("close duration in ms").isLessThan(PHASE_TIMEOUT_MS);
        });
    }

    @Test
    void lifecyclePhases_WhenRead_StopTheSchedulerBeforeLettuce() {
        Probe probe = new Probe(false);
        try (AnnotationConfigApplicationContext context = start(probe, FIRE_DELAY_MS)) {
            int scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class).getPhase();
            int lettuce = context.getBean("authRedisConnectionFactory", LettuceConnectionFactory.class).getPhase();

            assertThat(List.of(WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE,
                    WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE, scheduler, GATE_PHASE, lettuce))
                    .as("phases in stop order: graceful shutdown, web server stop, scheduler, gate, lettuce")
                    .containsExactly(Integer.MAX_VALUE - 1024, Integer.MAX_VALUE - 2048,
                            ExecutorConfigurationSupport.DEFAULT_PHASE, GATE_PHASE, 0)
                    .isSortedAccordingTo((a, b) -> Integer.compare(b, a));
            probe.lockAcquired.countDown();
            probe.lettuceStopped.countDown();
        }
    }

    private static AnnotationConfigApplicationContext start(Probe probe, long fireDelayMs) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("race",
                Map.of("planner.keyword-backfill.initial-delay-ms", String.valueOf(fireDelayMs))));
        context.registerBean(Probe.class, () -> probe);
        context.register(RaceConfig.class);
        context.refresh();
        probe.scheduler.set(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class));
        return context;
    }

    private static Class<?> loggingErrorHandler() throws ClassNotFoundException {
        return Class.forName("org.springframework.scheduling.support.TaskUtils$LoggingErrorHandler");
    }

    private static Long lockTtlSeconds() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getRedisHost(), redis.getRedisPort()));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            Long ttl = template.getExpire(LOCK_KEY);
            template.delete(LOCK_KEY);
            return ttl;
        } finally {
            factory.destroy();
        }
    }

    private static String describe(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        for (IThrowableProxy cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
            text.append(" <- ").append(cause.getClassName()).append(": ").append(cause.getMessage());
        }
        return text.toString();
    }

    static final class Probe {
        final CountDownLatch lockAcquired = new CountDownLatch(1);
        final CountDownLatch lettuceStopped = new CountDownLatch(1);
        final CountDownLatch unlockAttempted = new CountDownLatch(1);
        final CountDownLatch closeObserved = new CountDownLatch(1);
        final AtomicReference<Thread> worker = new AtomicReference<>();
        final AtomicReference<ThreadPoolTaskScheduler> scheduler = new AtomicReference<>();
        final AtomicInteger pages = new AtomicInteger();
        private final boolean firstPageFull;

        Probe(boolean firstPageFull) {
            this.firstPageFull = firstPageFull;
        }

        BackfillPage page(int limit) throws InterruptedException {
            worker.set(Thread.currentThread());
            lockAcquired.countDown();
            if (pages.getAndIncrement() == 0 && firstPageFull) {
                closeObserved.await();
                return new BackfillPage(UUID.randomUUID(), limit, 0);
            }
            lettuceStopped.await();
            return new BackfillPage(null, 0, 0);
        }
    }

    @Configuration
    @EnableScheduling
    @Import({ViewFlushSchedulerConfig.class, ShedLockConfig.class})
    static class RaceConfig {

        @Bean
        DefaultLifecycleProcessor lifecycleProcessor() {
            DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
            processor.setTimeoutPerShutdownPhase(PHASE_TIMEOUT_MS);
            return processor;
        }

        @Bean
        LettuceConnectionFactory authRedisConnectionFactory(Probe probe) {
            return new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(redis.getRedisHost(), redis.getRedisPort())) {
                @Override
                public void stop() {
                    super.stop();
                    probe.lettuceStopped.countDown();
                }

                @Override
                public RedisConnection getConnection() {
                    try {
                        return super.getConnection();
                    } finally {
                        if (Thread.currentThread() == probe.worker.get()) {
                            probe.unlockAttempted.countDown();
                        }
                    }
                }

                @Override
                public void destroy() {
                    if (probe.worker.get() != null) {
                        try {
                            probe.unlockAttempted.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    super.destroy();
                }
            };
        }

        @Bean
        PlannerKeywordBackfill plannerKeywordBackfill(Probe probe) {
            PlannerKeywordBackfillService service = new PlannerKeywordBackfillService(null, null, null, null) {
                @Override
                public BackfillPage backfillPage(UUID after, int limit) {
                    try {
                        return probe.page(limit);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new BackfillPage(null, 0, 0);
                    }
                }
            };
            return new PlannerKeywordBackfill(service);
        }

        @Bean
        SmartLifecycle closeObserver(Probe probe) {
            return new SmartLifecycle() {
                private volatile boolean running;

                @Override
                public void start() {
                    running = true;
                }

                @Override
                public void stop() {
                    running = false;
                    probe.closeObserved.countDown();
                }

                @Override
                public boolean isRunning() {
                    return running;
                }

                @Override
                public int getPhase() {
                    return Integer.MAX_VALUE;
                }
            };
        }

        @Bean
        SmartLifecycle gate(Probe probe) {
            return new SmartLifecycle() {
                private volatile boolean running;

                @Override
                public void start() {
                    running = true;
                }

                @Override
                public void stop() {
                    running = false;
                }

                @Override
                public void stop(Runnable callback) {
                    ScheduledThreadPoolExecutor executor = probe.scheduler.get().getScheduledThreadPoolExecutor();
                    if (executor.getQueue().isEmpty() && executor.getActiveCount() == 0) {
                        stop();
                        callback.run();
                        return;
                    }
                    Thread waiter = new Thread(() -> {
                        try {
                            probe.lockAcquired.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        running = false;
                        callback.run();
                    }, "race-gate");
                    waiter.setDaemon(true);
                    waiter.start();
                }

                @Override
                public boolean isRunning() {
                    return running;
                }

                @Override
                public int getPhase() {
                    return GATE_PHASE;
                }
            };
        }
    }
}
