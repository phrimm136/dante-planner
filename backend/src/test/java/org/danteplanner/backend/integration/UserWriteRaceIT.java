package org.danteplanner.backend.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LockWaits;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.user.service.UserSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class UserWriteRaceIT extends ExclusiveDatabaseSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserModerationService userModerationService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private Users users;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService racer = Executors.newSingleThreadExecutor();

    private TransactionTemplate concurrently;
    private User owner;
    private User admin;

    @BeforeEach
    void setUp() {
        concurrently = new TransactionTemplate(transactionManager);
        concurrently.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        owner = TestDataFactory.createTestUser(userRepository, "write-race-owner@example.com");
        admin = TestDataFactory.createAdmin(userRepository, "write-race-admin@example.com");
    }

    @AfterEach
    void stopRacer() {
        racer.shutdownNow();
    }

    @Test
    void updateUsernameEpithet_WhenABanCommitsAfterTheRead_KeepsTheBan() {
        afterABanCommits(() -> userService.updateUsernameEpithet(owner.getId(), "NAIVE"));

        assertBanned();
        assertThat(column("username_epithet", String.class)).isEqualTo("NAIVE");
    }

    @Test
    void updateUsernameEpithet_WhenTheAccountIsSoftDeleted_RefusesAsDeletedAndKeepsTheEpithet() {
        lifecycleService.deleteAccount(owner.getId());
        String before = column("username_epithet", String.class);

        assertRejected(() -> userService.updateUsernameEpithet(owner.getId(), "NAIVE")).withCode("UNAUTHORIZED");

        assertThat(column("username_epithet", String.class)).isEqualTo(before);
    }

    @Test
    void changeRole_WhenABanCommitsAfterTheRead_KeepsTheBan() {
        afterABanCommits(() -> users.changeRole(admin.getId(), owner.getId(), UserRole.MODERATOR));

        assertBanned();
        assertThat(column("role", String.class)).isEqualTo("MODERATOR");
    }

    @Test
    void deleteAccount_WhenABanCommitsAfterTheRead_KeepsTheBan() {
        afterABanCommits(() -> lifecycleService.deleteAccount(owner.getId()));

        assertBanned();
        assertThat(column("deleted_at IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void reactivateAccount_WhenABanCommitsAfterTheRead_KeepsTheBan() {
        lifecycleService.deleteAccount(owner.getId());

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userRepository.findById(owner.getId())).isPresent();
            concurrently.executeWithoutResult(other -> jdbcTemplate.update(
                    "UPDATE users SET banned_at = NOW(), banned_by = ? WHERE id = ?", admin.getId(), owner.getId()));
            lifecycleService.reactivateAccount(owner.getId());
        });

        assertBanned();
        assertThat(column("deleted_at IS NULL AND permanent_delete_scheduled_at IS NULL", Boolean.class)).isTrue();
    }

    @Test
    void banUser_WhenADeletionCommitsAfterTheRead_AnswersNotFoundAndKeepsTheDeletion() {
        assertRejected(() -> transactionTemplate.executeWithoutResult(status -> {
            assertThat(userRepository.findById(owner.getId())).isPresent();
            concurrently.executeWithoutResult(other -> lifecycleService.deleteAccount(owner.getId()));
            userModerationService.banUser(admin.getId(), owner.getId(), "spam");
        })).withCode("USER_NOT_FOUND");

        assertThat(column("deleted_at IS NOT NULL", Boolean.class)).isTrue();
        assertThat(column("banned_at IS NULL", Boolean.class)).isTrue();
        assertThat(auditRows()).isZero();
    }

    @Test
    void timeoutUser_WhenABanCommitsAfterTheRead_KeepsTheBan() {
        afterABanCommits(() -> userModerationService.timeoutUser(admin.getId(), owner.getId(), 60, "spam"));

        assertBanned();
        assertThat(column("timeout_until IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void timeoutUser_WhenAPromotionCommitsAfterThePolicyCheck_AnswersNotFoundAndWritesNothing() {
        refusedAfterAPromotion(() -> userModerationService.timeoutUser(admin.getId(), owner.getId(), 60, "spam"));

        assertThat(column("timeout_until IS NULL", Boolean.class)).isTrue();
    }

    @Test
    void removeTimeout_WhenAPromotionCommitsAfterThePolicyCheck_AnswersNotFoundAndWritesNothing() {
        userModerationService.timeoutUser(admin.getId(), owner.getId(), 60, "spam");

        refusedAfterAPromotion(() -> userModerationService.removeTimeout(admin.getId(), owner.getId(), "served"));

        assertThat(column("timeout_until IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void banUser_WhenAPromotionCommitsAfterThePolicyCheck_AnswersNotFoundAndWritesNothing() {
        refusedAfterAPromotion(() -> userModerationService.banUser(admin.getId(), owner.getId(), "spam"));

        assertThat(column("banned_at IS NULL", Boolean.class)).isTrue();
    }

    @Test
    void unbanUser_WhenAPromotionCommitsAfterThePolicyCheck_AnswersNotFoundAndWritesNothing() {
        userModerationService.banUser(admin.getId(), owner.getId(), "spam");

        refusedAfterAPromotion(() -> userModerationService.unbanUser(admin.getId(), owner.getId(), "appeal"));

        assertBanned();
    }

    @Test
    void deleteAccount_WhenRepeated_ReturnsTheFirstScheduleAndKeepsTheColumns() {
        Instant first = lifecycleService.deleteAccount(owner.getId());
        User deleted = userRepository.findById(owner.getId()).orElseThrow();

        Instant second = lifecycleService.deleteAccount(owner.getId());

        assertThat(second).isCloseTo(first, within(1, ChronoUnit.SECONDS));
        assertThat(userRepository.findById(owner.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(deleted);
    }

    @Test
    void reactivateAccount_WhenTheAccountIsActive_ChangesNothing() {
        User before = userRepository.findById(owner.getId()).orElseThrow();

        lifecycleService.reactivateAccount(owner.getId());

        assertThat(userRepository.findById(owner.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void updateSettings_WhenTheAccountIsSoftDeleted_RefusesAsDeletedAndKeepsTheSettings() {
        UserSettingsResponse before = userSettingsService.updateSettings(owner.getId(),
                new UpdateUserSettingsRequest(null, true, null, null));
        lifecycleService.deleteAccount(owner.getId());

        assertRejected(() -> userSettingsService.updateSettings(owner.getId(),
                new UpdateUserSettingsRequest(null, false, false, false))).withCode("UNAUTHORIZED");

        assertThat(storedSettings()).isEqualTo(before);
    }

    @Test
    void updateSettings_WhenOneFieldIsSent_LeavesTheOthers() {
        userSettingsService.updateSettings(owner.getId(), new UpdateUserSettingsRequest(true, false, null, true));

        UserSettingsResponse response = userSettingsService.updateSettings(owner.getId(),
                new UpdateUserSettingsRequest(null, null, false, null));

        assertThat(response).isEqualTo(new UserSettingsResponse(true, true, false, false, true));
        assertThat(storedSettings()).isEqualTo(response);
    }

    @Test
    void updateSettings_WhenASyncChoiceCommitsAfterTheRead_KeepsTheChoice() {
        userSettingsService.getOrCreateEntity(owner.getId());

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userSettingsRepository.findByUserId(owner.getId())).isPresent();
            concurrently.executeWithoutResult(other -> userSettingsService.updateSettings(owner.getId(),
                    new UpdateUserSettingsRequest(true, null, null, null)));
            userSettingsService.updateSettings(owner.getId(),
                    new UpdateUserSettingsRequest(null, false, null, null));
        });

        assertThat(storedSettings()).isEqualTo(new UserSettingsResponse(true, true, false, true, false));
    }

    @Test
    void updateSettings_WhenTwoFirstUpdatesRace_LeavesOneRowHoldingBoth() throws Exception {
        CompletableFuture<UserSettingsResponse> second = transactionTemplate.execute(status -> {
            userSettingsService.updateSettings(owner.getId(),
                    new UpdateUserSettingsRequest(null, false, null, null));
            CompletableFuture<UserSettingsResponse> racing = CompletableFuture.supplyAsync(
                    () -> userSettingsService.updateSettings(owner.getId(),
                            new UpdateUserSettingsRequest(null, null, false, null)), racer);
            LockWaits.await(jdbcTemplate, 1, racing::isDone, "the second first-time update waiting for the first");
            return racing;
        });

        assertThat(Hang.get(second, "the second first-time update settling"))
                .isEqualTo(new UserSettingsResponse(false, false, false, false, false));
        assertThat(storedSettings()).isEqualTo(new UserSettingsResponse(false, false, false, false, false));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_settings WHERE user_id = ?",
                Integer.class, owner.getId())).isEqualTo(1);
    }

    @RepeatedTest(20)
    void updateSettings_WhenFourFirstUpdatesRace_LeavesOneRowHoldingEveryField() throws Exception {
        List<UpdateUserSettingsRequest> requests = List.of(
                new UpdateUserSettingsRequest(true, null, null, null),
                new UpdateUserSettingsRequest(null, false, null, null),
                new UpdateUserSettingsRequest(null, null, false, null),
                new UpdateUserSettingsRequest(null, null, null, true));
        CyclicBarrier barrier = new CyclicBarrier(requests.size());
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        try {
            List<Future<UserSettingsResponse>> updates = requests.stream()
                    .map(request -> pool.submit(() -> {
                        Hang.await(barrier, "every first-time update reaching the barrier");
                        return userSettingsService.updateSettings(owner.getId(), request);
                    }))
                    .toList();
            for (Future<UserSettingsResponse> update : updates) {
                Hang.get(update, "a first-time update settling");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(storedSettings()).isEqualTo(new UserSettingsResponse(true, true, false, false, true));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_settings WHERE user_id = ?",
                Integer.class, owner.getId())).isEqualTo(1);
    }

    @Test
    void getOrCreateEntity_WhenTheRowIsCreated_HoldsTheDefaultsTheReadAnswers() {
        UserSettingsResponse defaults = userSettingsService.getSettings(owner.getId()).value();

        userSettingsService.getOrCreateEntity(owner.getId());

        assertThat(storedSettings()).isEqualTo(defaults);
    }

    @Test
    void getOrCreateEntity_WhenTheUserDoesNotExist_AnswersNotFound() {
        assertRejected(() -> userSettingsService.getOrCreateEntity(Long.MAX_VALUE)).withCode("USER_NOT_FOUND");
    }

    private void afterABanCommits(Runnable write) {
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userRepository.findById(owner.getId())).isPresent();
            concurrently.executeWithoutResult(other ->
                    userModerationService.banUser(admin.getId(), owner.getId(), "spam"));
            write.run();
        });
    }

    private void refusedAfterAPromotion(Runnable restriction) {
        int audited = auditRows();

        assertRejected(() -> transactionTemplate.executeWithoutResult(status -> {
            assertThat(userRepository.findById(owner.getId())).isPresent();
            concurrently.executeWithoutResult(other ->
                    users.changeRole(admin.getId(), owner.getId(), UserRole.MODERATOR));
            restriction.run();
        })).withCode("USER_NOT_FOUND");

        assertThat(column("role", String.class)).isEqualTo("MODERATOR");
        assertThat(auditRows()).isEqualTo(audited);
    }

    private int auditRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM moderation_actions WHERE target_uuid = ?",
                Integer.class, owner.getPublicId().toString());
    }

    private void assertBanned() {
        assertThat(column("banned_at IS NOT NULL", Boolean.class))
                .as("the ban committed after the write read the row")
                .isTrue();
        assertThat(column("banned_by", Long.class)).isEqualTo(admin.getId());
    }

    private UserSettingsResponse storedSettings() {
        return UserSettingsResponse.fromEntity(userSettingsRepository.findByUserId(owner.getId()).orElseThrow());
    }

    private <T> T column(String expression, Class<T> type) {
        return jdbcTemplate.queryForObject("SELECT " + expression + " FROM users WHERE id = ?", type, owner.getId());
    }
}
