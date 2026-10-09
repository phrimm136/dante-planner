package org.danteplanner.backend.integration;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
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

class AdminWithdrawalWriteSkewIT extends ExclusiveDatabaseSupport {

    private static final int ROUNDS = 20;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void withdrawAndSelfDemote_WhenTheLastTwoAdminsActConcurrently_LetExactlyOneThrough() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                jdbcTemplate.update("UPDATE users SET role = 'NORMAL' WHERE role = 'ADMIN'");
                Long withdrawing = TestDataFactory
                        .createAdmin(userRepository, "withdraw-" + UUID.randomUUID() + "@example.com").getId();
                Long demoting = TestDataFactory
                        .createAdmin(userRepository, "demote-" + UUID.randomUUID() + "@example.com").getId();

                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<?> withdrawal = pool.submit(() -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    return lifecycleService.deleteAccount(withdrawing);
                });
                Future<?> demotion = pool.submit(() -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    return adminService.changeRole(demoting, demoting, UserRole.NORMAL);
                });

                int succeeded = settle(withdrawal) + settle(demotion);

                assertThat(succeeded).as("successful attempts in round %d", round).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id IN (?, ?) AND role = 'ADMIN' AND deleted_at IS NULL",
                        Long.class, withdrawing, demoting))
                        .as("active administrators left in round %d", round)
                        .isEqualTo(1L);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private int settle(Future<?> attempt) throws Exception {
        try {
            attempt.get(15, TimeUnit.SECONDS);
            return 1;
        } catch (ExecutionException losing) {
            assertThat(losing.getCause())
                    .as("a losing attempt is refused by the last-admin guard or rolled back as a deadlock")
                    .satisfiesAnyOf(
                            refusal -> assertThat(refusal).isInstanceOf(LastAdministratorException.class),
                            rollback -> assertThat(rollback).isInstanceOf(CannotAcquireLockException.class));
            return 0;
        }
    }
}
