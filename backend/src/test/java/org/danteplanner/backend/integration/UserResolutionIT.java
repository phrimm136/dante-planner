package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User resolution seam: resolving a brand-new provider identity creates the user and its
 * settings row together, two concurrent first-logins for the same identity converge on a
 * single user with both callers succeeding, and resolving a returning user writes nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UserResolutionIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "user_resolution_it");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @BeforeEach
    void setUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
    }

    private Map<String, String> identity(String providerId) {
        return Map.of("id", providerId, "email", providerId + "@example.com");
    }

    @Test
    @DisplayName("userResolve_WhenNewUser_CreatesSettingsRowAtomically")
    void userResolve_WhenNewUser_CreatesSettingsRowAtomically() {
        User created = userService.findOrCreateUser("google", identity("resolve-new-001"));

        assertThat(created.getId()).isNotNull();
        assertThat(userSettingsRepository.findByUserId(created.getId()))
                .as("a newly resolved user must get its settings row in the same resolution")
                .isPresent();
    }

    @Test
    @DisplayName("oauthRace_WhenDuplicateKeyLoser_RetriesAndFinds")
    void oauthRace_WhenDuplicateKeyLoser_RetriesAndFinds() throws Exception {
        Map<String, String> identity = identity("resolve-race-001");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<User> a = pool.submit(() -> raced(barrier, identity));
            Future<User> b = pool.submit(() -> raced(barrier, identity));

            User ra = a.get(15, TimeUnit.SECONDS);
            User rb = b.get(15, TimeUnit.SECONDS);

            assertThat(ra).as("first caller resolves a user").isNotNull();
            assertThat(rb).as("second caller resolves a user").isNotNull();
            assertThat(ra.getId()).isEqualTo(rb.getId());
            assertThat(userRepository.findByProviderAndProviderId(
                    org.danteplanner.backend.auth.entity.AuthProviderType.GOOGLE, "resolve-race-001"))
                    .isPresent();
            assertThat(userRepository.findAll().stream()
                    .filter(u -> "resolve-race-001".equals(u.getProviderId()))
                    .count())
                    .as("the race must converge on exactly one user")
                    .isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    private User raced(CyclicBarrier barrier, Map<String, String> identity) throws Exception {
        barrier.await(15, TimeUnit.SECONDS);
        return userService.findOrCreateUser("google", identity);
    }

    @Test
    @DisplayName("userResolve_WhenReturningUser_NoWrites")
    void userResolve_WhenReturningUser_NoWrites() {
        User existing = TestDataFactory.createTestUser(userRepository, "returning@example.com");
        long before = userRepository.count();

        User resolved = userService.findOrCreateUser("google", Map.of(
                "id", existing.getProviderId(), "email", existing.getEmail()));

        assertThat(resolved.getId()).isEqualTo(existing.getId());
        assertThat(userRepository.count())
                .as("resolving a returning user inserts nothing")
                .isEqualTo(before);
    }
}
