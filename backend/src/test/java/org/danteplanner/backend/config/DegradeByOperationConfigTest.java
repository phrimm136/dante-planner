package org.danteplanner.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("the scheduler pool has room for a stalled task without starving SSE heartbeats")
    void schedulerNotStarvedByCrossRegionHang_WhenTaskStalls_PoolHasSpareThreads() throws IOException {
        String poolSize = load("/application.properties").getProperty("spring.task.scheduling.pool.size");

        assertThat(poolSize)
                .as("spring.task.scheduling.pool.size must be set; the default single thread lets one "
                        + "hung cross-region task stop every other scheduled task")
                .isNotNull();
        assertThat(Integer.parseInt(poolSize.trim())).isGreaterThanOrEqualTo(MIN_SCHEDULER_POOL);
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
        return Integer.parseInt(value);
    }
}
