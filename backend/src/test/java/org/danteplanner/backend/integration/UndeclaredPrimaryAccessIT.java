package org.danteplanner.backend.integration;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A primary connection acquired with no transaction active is undeclared, and the pod boots strict
 * here: the acquisition is rejected rather than counted. Boot-time acquisitions are exempt by the
 * context having started at all — the guard arms at {@code ApplicationReadyEvent}, after Flyway and
 * schema validation have run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UndeclaredPrimaryAccessIT extends SharedRoutingSupport {

    private static final String PROBE_QUERY = "SELECT 1";
    private static final String UNDECLARED_MESSAGE = "outside a transaction";
    private static final Duration RESPONSE_HEADERS_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModerationAuditService moderationAuditService;

    @Autowired
    private PlannerRepository plannerRepository;

    @LocalServerPort
    private int port;

    private UUID publishedPlannerId;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "undeclared-primary-owner@example.com");
        publishedPlannerId = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository)
                .getId();
    }

    @Test
    void primaryAcquisition_WhenNoTransactionIsActive_IsRejected() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.queryForObject(PROBE_QUERY, Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNDECLARED_MESSAGE);
    }

    @Test
    void declaredFinder_WhenCalledOutsideATransaction_IsRejected() {
        assertThatThrownBy(() -> userRepository.findByProviderAndProviderId(
                        AuthProviderType.GOOGLE, UUID.randomUUID().toString()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNDECLARED_MESSAGE);
    }

    @Test
    void inheritedFindById_WhenCalledOutsideATransaction_IsServed() {
        assertThatCode(() -> userRepository.findById(Long.MAX_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void latestBanReason_WhenCalledOutsideATransaction_IsServed() {
        assertThatCode(() -> moderationAuditService.latestBanReason(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void latestTimeoutReason_WhenCalledOutsideATransaction_IsServed() {
        assertThatCode(() -> moderationAuditService.latestTimeoutReason(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void primaryAcquisition_WhenInsideAWriteTransaction_IsServed() {
        assertThat(probeInTransaction(false)).isEqualTo(1);
    }

    @Test
    void replicaAcquisition_WhenInsideAReadOnlyTransaction_IsServed() {
        assertThat(probeInTransaction(true)).isEqualTo(1);
    }

    @Test
    void subscribeToComments_WhenAnonymousOnPublishedPlanner_StartsEventStream() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/planner/" + publishedPlannerId + "/comments/events"))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .timeout(RESPONSE_HEADERS_TIMEOUT)
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = response.body()) {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Type"))
                        .hasValueSatisfying(type -> assertThat(type).contains(MediaType.TEXT_EVENT_STREAM_VALUE));
            }
        }
    }

    private Integer probeInTransaction(boolean readOnly) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setReadOnly(readOnly);
        return transactionTemplate.execute(status ->
                new JdbcTemplate(dataSource).queryForObject(PROBE_QUERY, Integer.class));
    }
}
