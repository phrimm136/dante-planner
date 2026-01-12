package org.danteplanner.backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes required test data for the H2 test database.
 *
 * <p>Creates the sentinel user (id=0) required for vote reassignment during user deletion.
 * Uses native SQL to bypass JPA's IDENTITY generation which ignores explicit ID values.
 *
 * @see SentinelUserVerifier
 */
@TestConfiguration
@Profile("test")
@Slf4j
@Order(1)
public class TestDataInitializer {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    @Transactional
    public void initializeTestData() {
        log.info("Initializing test data...");
        createSentinelUserNative();
    }

    /**
     * Creates the sentinel user (id=0) using native SQL.
     * JPA's IDENTITY generation ignores explicit ID values, so native SQL is required.
     */
    private void createSentinelUserNative() {
        if (userRepository.existsById(0L)) {
            log.info("Sentinel user (id=0) already exists");
            return;
        }

        // Use native SQL to insert with explicit ID (bypasses IDENTITY generation)
        entityManager.createNativeQuery(
                "INSERT INTO users (id, email, provider, provider_id, username_keyword, username_suffix, role, created_at, updated_at) " +
                "VALUES (0, '[deleted]', 'system', 'DELETED_USER_SENTINEL', 'DELETED', '00000', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .executeUpdate();

        log.info("Created sentinel user (id=0) for test environment");
    }

    /**
     * Recreates the sentinel user after deleteAll() in tests.
     * Call this in @BeforeEach after userRepository.deleteAll().
     *
     * @param entityManager EntityManager for native query execution
     */
    public static void createSentinelUser(EntityManager entityManager) {
        entityManager.createNativeQuery(
                "INSERT INTO users (id, email, provider, provider_id, username_keyword, username_suffix, role, created_at, updated_at) " +
                "VALUES (0, '[deleted]', 'system', 'DELETED_USER_SENTINEL', 'DELETED', '00000', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .executeUpdate();
    }

    /**
     * @deprecated Use {@link #createSentinelUser(EntityManager)} instead.
     * JPA repository cannot insert with explicit ID when using IDENTITY generation.
     */
    @Deprecated
    public static org.danteplanner.backend.entity.User createSentinelUser(UserRepository userRepository) {
        throw new UnsupportedOperationException(
                "Cannot create sentinel user via JPA - use createSentinelUser(EntityManager) instead");
    }
}
