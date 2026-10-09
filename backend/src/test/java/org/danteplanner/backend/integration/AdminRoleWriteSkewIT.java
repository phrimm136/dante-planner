package org.danteplanner.backend.integration;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
 * ({@code COUNT(*) WHERE role='ADMIN' AND deleted_at IS NULL}), which is write skew exactly: each transaction preserves
 * "at least one admin" on its own, and together they do not.</p>
 *
 * <p>Takes the exclusive database and demotes every administrator in it first, because the
 * safeguard reads a whole-table predicate. An admin created by another class counts toward it, and
 * the invariant asserted here would then hold for reasons this test did not create.</p>
 *
 * <p>The assertion is the invariant, not the mechanism. Whichever way the two transactions
 * resolve — serialized, so the loser is refused by the safeguard, or interleaved, so InnoDB breaks
 * the deadlock — an administrator must remain.</p>
 */
class AdminRoleWriteSkewIT extends ExclusiveDatabaseSupport {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void changeRole_WhenBothRemainingAdminsSelfDemoteConcurrently_LeavesAnAdmin() throws Exception {
        jdbcTemplate.update("UPDATE users SET role = 'NORMAL' WHERE role = 'ADMIN'");
        Long firstAdmin = TestDataFactory
                .createAdmin(userRepository, "skew-a-" + UUID.randomUUID() + "@example.com").getId();
        Long secondAdmin = TestDataFactory
                .createAdmin(userRepository, "skew-b-" + UUID.randomUUID() + "@example.com").getId();

        assertThat(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN))
                .as("the skew only exists when these two are the last administrators")
                .isEqualTo(2L);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<?> a = pool.submit(() -> selfDemote(barrier, firstAdmin));
            Future<?> b = pool.submit(() -> selfDemote(barrier, secondAdmin));

            awaitSettled(a);
            awaitSettled(b);

            assertThat(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN))
                    .as("both self-demotions must not commit: the system cannot be left without an "
                            + "administrator, whichever transaction wins")
                    .isEqualTo(1L);
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
     * Waits for a demotion to finish. A losing one is either refused by the safeguard or rolled back
     * by the database as a deadlock; any other failure fails the test.
     */
    private void awaitSettled(Future<?> attempt) throws Exception {
        try {
            attempt.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException losing) {
            assertThat(losing.getCause())
                    .as("a losing self-demotion is refused by the last-admin safeguard or rolled back as a deadlock")
                    .satisfiesAnyOf(
                            refusal -> assertThat(refusal)
                                    .isInstanceOf(LastAdministratorException.class),
                            rollback -> assertThat(rollback).isInstanceOf(CannotAcquireLockException.class));
        }
    }
}
