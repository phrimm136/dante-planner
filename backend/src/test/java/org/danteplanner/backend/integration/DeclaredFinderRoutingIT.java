package org.danteplanner.backend.integration;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.boot.test.context.SpringBootTest;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository reads made with no transaction active, against the routing datasource with the
 * undeclared-primary guard counting rather than rejecting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class DeclaredFinderRoutingIT extends SharedMySqlContainerSupport {

    private static final String UNDECLARED_COUNTER = "datasource.primary.undeclared";

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        String url = registerSharedMysql(registry);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.routing.undeclared-primary-fail-fast", () -> "false");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", () -> url);
        registry.add("datasource.replica.username", MYSQL::getUsername);
        registry.add("datasource.replica.password", MYSQL::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModerationAuditService moderationAuditService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void declaredFinder_WhenCalledOutsideATransaction_CountsAnUndeclaredPrimaryAcquisition() {
        double before = undeclaredCount();

        userRepository.findByProviderAndProviderId(AuthProviderType.GOOGLE, UUID.randomUUID().toString());

        assertThat(undeclaredCount() - before).isEqualTo(1.0);
    }

    @Test
    void inheritedFindById_WhenCalledOutsideATransaction_CountsNothing() {
        double before = undeclaredCount();

        userRepository.findById(Long.MAX_VALUE);

        assertThat(undeclaredCount() - before).isZero();
    }

    @Test
    void latestBanReason_WhenCalledOutsideATransaction_CountsNothing() {
        double before = undeclaredCount();

        moderationAuditService.latestBanReason(UUID.randomUUID());

        assertThat(undeclaredCount() - before).isZero();
    }

    @Test
    void latestTimeoutReason_WhenCalledOutsideATransaction_CountsNothing() {
        double before = undeclaredCount();

        moderationAuditService.latestTimeoutReason(UUID.randomUUID());

        assertThat(undeclaredCount() - before).isZero();
    }

    private double undeclaredCount() {
        return meterRegistry.get(UNDECLARED_COUNTER).counter().count();
    }
}
