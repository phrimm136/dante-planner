package org.danteplanner.backend.integration;

import java.util.UUID;

import javax.sql.DataSource;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class UndeclaredPrimaryAccessIT extends SharedMySqlContainerSupport {

    private static final String PROBE_QUERY = "SELECT 1";
    private static final String UNDECLARED_MESSAGE = "outside a transaction";

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        String url = registerSharedMysql(registry);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", () -> url);
        registry.add("datasource.replica.username", MYSQL::getUsername);
        registry.add("datasource.replica.password", MYSQL::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModerationAuditService moderationAuditService;

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

    private Integer probeInTransaction(boolean readOnly) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setReadOnly(readOnly);
        return transactionTemplate.execute(status ->
                new JdbcTemplate(dataSource).queryForObject(PROBE_QUERY, Integer.class));
    }
}
