package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counter-guard seam: the denormalized {@code view_count} column is
 * {@code updatable=false}, so a stale entity flush cannot clobber a concurrent bulk
 * increment. Loaded at N, bulk-incremented to N+1, then flushed — the increment survives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
@Transactional
class PlannerCounterGuardIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_counter_guard_it");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        author = TestDataFactory.createTestUser(userRepository, "author@example.com");
    }

    @Test
    @DisplayName("entitySave_WhenBulkIncremented_PreservesCounters")
    void entitySave_WhenBulkIncremented_PreservesCounters() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(author)
                .title("Counter Guard")
                .category("5F")
                .status(PlannerStatus.SAVED)
                .content("{}")
                .schemaVersion(2)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .viewCount(5)
                .build();
        UUID id = plannerRepository.saveAndFlush(planner).getId();
        entityManager.clear();

        // Load the entity at N=5, then let a bulk increment commit N+1=6 (this also
        // detaches the loaded entity via clearAutomatically).
        Planner loaded = plannerRepository.findById(id).orElseThrow();
        assertThat(loaded.getViewCount()).isEqualTo(5);

        plannerRepository.incrementViewCount(id);

        // Flushing the stale entity must not write its N=5 view_count back over the increment.
        loaded.setTitle("touched after increment");
        plannerRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        Planner reloaded = plannerRepository.findById(id).orElseThrow();
        assertThat(reloaded.getViewCount()).isEqualTo(6);
    }
}
