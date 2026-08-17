package org.danteplanner.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.danteplanner.backend.planner.config.ViewFlushSchedulerConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the configuration that bounds a cross-region write so it degrades by operation: a hung
 * primary must fail writes within the socket timeout instead of holding request threads, and the
 * scheduler must have enough threads that one stalled task cannot stop SSE heartbeats.
 */
class DegradeByOperationConfigTest {

    private static final int MIN_SCHEDULER_POOL = 4;
    private static final int MAX_SOCKET_TIMEOUT_MS = 15_000;

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = DegradeByOperationConfigTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    @DisplayName("the scheduler a stalled task shares has spare threads, and the view flush has its own")
    void schedulerNotStarvedByCrossRegionHang_WhenTaskStalls_PoolHasSpareThreads() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(ViewFlushSchedulerConfig.class)
                .withPropertyValues("spring.task.scheduling.pool.size=4")
                .run(context -> {
                    ThreadPoolTaskScheduler shared = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                    ThreadPoolTaskScheduler viewFlush = context.getBean(
                            ViewFlushSchedulerConfig.VIEW_FLUSH_SCHEDULER, ThreadPoolTaskScheduler.class);

                    assertThat(shared.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .as("the scheduler shared by every @Scheduled task must have spare threads, "
                                    + "or one task blocked on a cross-region write stops the rest")
                            .isGreaterThanOrEqualTo(MIN_SCHEDULER_POOL);
                    assertThat(viewFlush)
                            .as("the 500ms view flush must not share the pool it could exhaust")
                            .isNotSameAs(shared);
                    assertThat(viewFlush.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("the application JDBC url bounds connect and socket waits, while Flyway's stays unbounded")
    void writeHangReadsSurvive_WhenPrimaryHangs_AppJdbcUrlBoundsSocketWait() throws IOException {
        Properties prod = load("/application-prod.properties");
        String appUrl = prod.getProperty("spring.datasource.url");
        String replicaUrl = prod.getProperty("datasource.replica.url");
        String flywayUrl = prod.getProperty("spring.flyway.url");

        assertThat(socketTimeoutOf(appUrl))
                .as("the application url must bound the socket wait so a blackholed primary fails "
                        + "the write instead of pinning a request thread")
                .isNotNull()
                .isLessThanOrEqualTo(MAX_SOCKET_TIMEOUT_MS);
        assertThat(appUrl).as("the application url must bound the connect wait").contains("connectTimeout=");

        assertThat(socketTimeoutOf(replicaUrl))
                .as("the replica url must bound the socket wait too")
                .isNotNull()
                .isLessThanOrEqualTo(MAX_SOCKET_TIMEOUT_MS);

        assertThat(flywayUrl)
                .as("Flyway must stay unbounded; a long migration would abort at the socket timeout")
                .doesNotContain("socketTimeout=");
    }

    private static Integer socketTimeoutOf(String url) {
        if (url == null) {
            return null;
        }
        int at = url.indexOf("socketTimeout=");
        if (at < 0) {
            return null;
        }
        String value = url.substring(at + "socketTimeout=".length()).split("&")[0];
        return Integer.parseInt(defaultOfPlaceholder(value));
    }

    /** Resolves a {@code ${VAR:default}} property placeholder to its default, or returns the literal. */
    private static String defaultOfPlaceholder(String value) {
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return value.trim();
        }
        String body = value.substring(2, value.length() - 1);
        int colon = body.indexOf(':');
        return colon < 0 ? body.trim() : body.substring(colon + 1).trim();
    }
}
