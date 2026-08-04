package org.danteplanner.backend.integration;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last-admin safeguard against write skew.
 *
 * <p>Self-demotion is the only route by which an administrator's role drops: safeguard 2 rejects
 * an actor modifying anyone of equal or higher rank, so one admin cannot demote another. Two
 * admins self-demoting therefore lock disjoint rows while both reading the same predicate
 * ({@code COUNT(*) WHERE role='ADMIN'}), which is write skew exactly: each transaction preserves
 * "at least one admin" on its own, and together they do not.</p>
 *
 * <p>Takes its own database because the safeguard reads a whole-table predicate. An admin created
 * by a neighbouring class in the shared database counts toward it, and the invariant asserted here
 * would then hold for reasons this test did not create.</p>
 *
 * <p>The assertion is the invariant, not the mechanism. Whichever way the two transactions
 * resolve — serialized, so the loser is refused by the safeguard, or interleaved, so InnoDB breaks
 * the deadlock — an administrator must remain.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class AdminRoleWriteSkewIT {

    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "admin_role_skew");
    }

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void changeRole_WhenBothRemainingAdminsSelfDemoteConcurrently_LeavesAnAdmin() throws Exception {
        Long firstAdmin = TestDataFactory
                .createAdmin(userRepository, "skew-a-" + UUID.randomUUID() + "@example.com").getId();
        Long secondAdmin = TestDataFactory
                .createAdmin(userRepository, "skew-b-" + UUID.randomUUID() + "@example.com").getId();

        assertThat(userRepository.countByRole(UserRole.ADMIN))
                .as("the skew only exists when these two are the last administrators")
                .isEqualTo(2L);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<?> a = pool.submit(() -> selfDemote(barrier, firstAdmin));
            Future<?> b = pool.submit(() -> selfDemote(barrier, secondAdmin));

            awaitSettled(a);
            awaitSettled(b);

            assertThat(userRepository.countByRole(UserRole.ADMIN))
                    .as("both self-demotions must not commit: the system cannot be left without an "
                            + "administrator, whichever transaction wins")
                    .isGreaterThanOrEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Runs one self-demotion, releasing only once both callers have reached the barrier so their
     * predicate reads overlap.
     */
    private Object selfDemote(CyclicBarrier barrier, Long adminId) throws Exception {
        barrier.await(15, TimeUnit.SECONDS);
        return adminService.changeRole(adminId, adminId, UserRole.NORMAL);
    }

    /**
     * Waits for a demotion to finish either way. A refusal by the safeguard and a rollback forced
     * by the database are both correct outcomes; only the surviving admin count is asserted.
     */
    private void awaitSettled(Future<?> attempt) throws Exception {
        try {
            attempt.get(15, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException expected) {
            // A losing transaction reports as ModerationForbiddenException (serialized: the
            // safeguard saw one admin left) or as a data-access failure (interleaved: InnoDB rolled
            // one back). Both leave the invariant intact, which the caller asserts.
        }
    }
}
