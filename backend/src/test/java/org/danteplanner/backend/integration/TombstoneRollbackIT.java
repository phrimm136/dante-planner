package org.danteplanner.backend.integration;

import java.util.UUID;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hardening acceptance test for finding F1: the delete-path tombstone is written to Redis
 * synchronously inside the SQL transaction, so a transaction that rolls back reverts the
 * soft-delete (entity stays LIVE) yet leaves the tombstone behind for up to 1h, 404-ing a live
 * entity from stale replicas.
 *
 * <p>Drives the REAL {@link PlannerCommandService#deletePlanner} through an outer
 * {@link TransactionTemplate} that forces a rollback ({@code setRollbackOnly}); because
 * {@code deletePlanner} is {@code @Transactional(REQUIRED)} it joins the outer transaction, so the
 * soft-delete is rolled back with it. The contract: after the rollback there must be NO
 * {@code del:planner:<id>} tombstone (and the planner must still resolve as live).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class TombstoneRollbackIT extends CausalHarnessSupport {

    @Autowired
    private PlannerCommandService plannerCommandService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private ContentTombstoneStore contentTombstoneStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("A delete whose transaction rolls back must leave no tombstone for the still-live planner")
    void deletePlanner_WhenTransactionRollsBack_LeavesNoTombstone() {
        User owner = TestDataFactory.createTestUser(
                userRepository, "f1-tombstone-rollback-" + UUID.randomUUID() + "@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        Long userId = owner.getId();
        UUID plannerId = planner.getId();
        UUID deviceId = UUID.randomUUID();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            plannerCommandService.deletePlanner(userId, deviceId, plannerId);
            status.setRollbackOnly();
        });

        assertThat(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, userId))
                .as("the rollback must revert the soft-delete: the planner is still live")
                .isPresent();

        assertThat(contentTombstoneStore.isTombstoned(ByIdReadGuard.PLANNER_ENTITY_TYPE, plannerId))
                .as("a rolled-back delete must leave no tombstone that would 404 the still-live planner")
                .isFalse();
    }
}
