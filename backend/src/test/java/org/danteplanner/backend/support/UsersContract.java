package org.danteplanner.backend.support;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public interface UsersContract {

    long UNKNOWN_ID = Long.MAX_VALUE;

    Users users();

    User persisted(User user);

    Long liveSentinelId();

    Class<? extends RuntimeException> duplicateKeyFailure();

    default <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Test
    default void findActiveById_WhenTheUserIsLive_ReturnsItsSummary() {
        User user = persisted(newUser().build());

        assertThat(users().findActiveById(user.getId())).hasValueSatisfying(summary -> {
            assertThat(summary.id()).isEqualTo(user.getId());
            assertThat(summary.publicId()).isEqualTo(user.getPublicId());
            assertThat(summary.email()).isEqualTo(user.getEmail());
            assertThat(summary.usernameEpithet()).isEqualTo(user.getUsernameEpithet());
            assertThat(summary.usernameSuffix()).isEqualTo(user.getUsernameSuffix());
            assertThat(summary.role()).isEqualTo(UserRole.NORMAL);
            assertThat(summary.deleted()).isFalse();
        });
    }

    @Test
    default void findActiveById_WhenTheUserIsDeleted_ReturnsEmpty() {
        User user = persisted(deletedUser().build());

        assertThat(users().findActiveById(user.getId())).isEmpty();
    }

    @Test
    default void findActiveById_WhenTheUserIsBannedAndTimedOut_StillReturnsIt() {
        User user = persisted(restrictedUser().build());

        assertThat(users().findActiveById(user.getId())).hasValueSatisfying(summary -> {
            assertThat(summary.isBanned()).isTrue();
            assertThat(summary.isTimedOut()).isTrue();
        });
    }

    @Test
    default void findActiveById_WhenTheIdIsUnknown_ReturnsEmpty() {
        assertThat(users().findActiveById(UNKNOWN_ID)).isEmpty();
    }

    @Test
    default void lockActiveById_WhenTheUserIsLive_ReturnsItsRow() {
        User user = persisted(newUser().build());

        assertThat(inTransaction(() -> users().lockActiveById(user.getId())).id()).isEqualTo(user.getId());
    }

    @Test
    default void lockActiveById_WhenTheUserIsDeleted_ThrowsUserNotFound() {
        User user = persisted(deletedUser().build());

        assertThatThrownBy(() -> inTransaction(() -> users().lockActiveById(user.getId())))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + user.getId());
    }

    @Test
    default void holdActiveById_WhenTheUserIsLive_ReturnsItsRow() {
        User user = persisted(newUser().build());

        assertThat(inTransaction(() -> users().holdActiveById(user.getId())).id()).isEqualTo(user.getId());
    }

    @Test
    default void holdActiveById_WhenTheUserIsDeleted_ThrowsAccountDeleted() {
        User user = persisted(deletedUser().build());

        assertThatThrownBy(() -> inTransaction(() -> users().holdActiveById(user.getId())))
                .isInstanceOf(AccountDeletedException.class)
                .hasMessage("Account with ID " + user.getId() + " has been deleted");
    }

    @Test
    default void findById_WhenTheUserIsDeleted_ReturnsItMarkedDeleted() {
        User user = persisted(deletedUser().build());

        assertThat(users().findById(user.getId()).deleted()).isTrue();
    }

    @Test
    default void findById_WhenTheIdIsUnknown_ThrowsUserNotFound() {
        assertThatThrownBy(() -> users().findById(UNKNOWN_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + UNKNOWN_ID);
    }

    @Test
    default void findOptionalById_WhenTheUserIsDeleted_ReturnsIt() {
        User user = persisted(deletedUser().build());

        assertThat(users().findOptionalById(user.getId())).hasValueSatisfying(
                summary -> assertThat(summary.deleted()).isTrue());
    }

    @Test
    default void findOptionalById_WhenTheIdIsUnknown_ReturnsEmpty() {
        assertThat(users().findOptionalById(UNKNOWN_ID)).isEmpty();
    }

    @Test
    default void findByProvider_WhenTheUserIsDeleted_ReturnsIt() {
        User user = persisted(deletedUser().build());

        assertThat(users().findByProvider(AuthProviderType.GOOGLE, user.getProviderId()))
                .map(UserSummary::id).hasValue(user.getId());
    }

    @Test
    default void findActiveByProvider_WhenTheUserIsLive_ReturnsIt() {
        User user = persisted(newUser().build());

        assertThat(users().findActiveByProvider(AuthProviderType.GOOGLE, user.getProviderId()))
                .map(UserSummary::id).hasValue(user.getId());
    }

    @Test
    default void findActiveByProvider_WhenTheUserIsDeleted_ReturnsEmpty() {
        User user = persisted(deletedUser().build());

        assertThat(users().findActiveByProvider(AuthProviderType.GOOGLE, user.getProviderId())).isEmpty();
    }

    @Test
    default void findActiveBySuffix_WhenTheUserIsLive_ReturnsIt() {
        User user = persisted(newUser().build());

        assertThat(users().findActiveBySuffix(user.getUsernameSuffix()))
                .map(UserSummary::id).hasValue(user.getId());
    }

    @Test
    default void findActiveBySuffix_WhenTheUserIsDeleted_ReturnsEmpty() {
        User user = persisted(deletedUser().build());

        assertThat(users().findActiveBySuffix(user.getUsernameSuffix())).isEmpty();
    }

    @Test
    default void findActiveBySuffix_WhenTheCaseDiffers_StillFindsIt() {
        User user = persisted(newUser().usernameSuffix(TestDataFactory.uniqueSuffix("c")).build());

        assertThat(users().findActiveBySuffix(user.getUsernameSuffix().toUpperCase(Locale.ROOT)))
                .map(UserSummary::id).hasValue(user.getId());
    }

    @Test
    default void findByProvider_WhenTheProviderIdCaseDiffers_StillFindsIt() {
        User user = persisted(newUser().build());

        assertThat(users().findByProvider(AuthProviderType.GOOGLE, user.getProviderId().toUpperCase(Locale.ROOT)))
                .map(UserSummary::id).hasValue(user.getId());
    }

    @Test
    default void persisted_WhenTheProviderIdIsTaken_IsRejectedByTheProviderKey() {
        User user = persisted(newUser().build());

        assertThatThrownBy(() -> persisted(newUser().providerId(user.getProviderId()).build()))
                .isInstanceOf(duplicateKeyFailure())
                .hasMessageContaining("uk_provider_provider_id");
    }

    @Test
    default void persisted_WhenTheSuffixIsTaken_IsRejectedByTheSuffixKey() {
        User user = persisted(newUser().build());

        assertThatThrownBy(() -> persisted(newUser().usernameSuffix(user.getUsernameSuffix()).build()))
                .isInstanceOf(duplicateKeyFailure())
                .hasMessageContaining("uk_users_username_suffix");
    }

    @Test
    default void persisted_WhenTheSuffixDiffersOnlyInCase_IsRejectedByTheSuffixKey() {
        User user = persisted(newUser().usernameSuffix(TestDataFactory.uniqueSuffix("c")).build());

        assertThatThrownBy(() -> persisted(newUser()
                        .usernameSuffix(user.getUsernameSuffix().toUpperCase(Locale.ROOT)).build()))
                .isInstanceOf(duplicateKeyFailure())
                .hasMessageContaining("uk_users_username_suffix");
    }

    @Test
    default void findAllByIds_WhenAnIdIsUnknown_ReturnsTheKnownRowsDeletedIncluded() {
        User live = persisted(newUser().build());
        User deleted = persisted(deletedUser().build());

        assertThat(users().findAllByIds(List.of(live.getId(), deleted.getId(), UNKNOWN_ID)))
                .extracting(UserSummary::id)
                .containsExactlyInAnyOrder(live.getId(), deleted.getId());
    }

    @Test
    default void listActiveAccounts_WhenUsersAreLiveAndDeleted_ListsOnlyTheLive() {
        User live = persisted(newUser().build());
        User restricted = persisted(restrictedUser().build());
        User deleted = persisted(deletedUser().build());

        assertThat(users().listActiveAccounts())
                .extracting(UserSummary::id)
                .contains(live.getId(), restricted.getId())
                .doesNotContain(deleted.getId());
    }

    @Test
    default void listActiveAccounts_WhenTheSentinelIsLive_LeavesItOut() {
        Long sentinel = liveSentinelId();

        assertThat(users().findActiveById(sentinel)).isPresent();
        assertThat(users().listActiveAccounts()).extracting(UserSummary::id).doesNotContain(sentinel);
    }

    @Test
    default void listTimedOutAccounts_WhenTimeoutsArePastFutureOrOnADeletedUser_ListsOnlyTheLiveFutureOne() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        User timedOut = persisted(newUser().timeoutUntil(now.plus(Duration.ofHours(1))).build());
        User expired = persisted(newUser().timeoutUntil(now.minus(Duration.ofHours(1))).build());
        User deleted = persisted(deletedUser().timeoutUntil(now.plus(Duration.ofHours(1))).build());

        assertThat(users().listTimedOutAccounts())
                .extracting(UserSummary::id)
                .contains(timedOut.getId())
                .doesNotContain(expired.getId(), deleted.getId());
    }

    @Test
    default void findOrCreateUser_WhenTheProviderIdIsNew_CreatesALiveNormalUser() {
        String providerId = "google-" + UUID.randomUUID();
        String email = "created+" + UUID.randomUUID() + "@example.com";

        UserSummary created = users().findOrCreateUser("google", Map.of("id", providerId, "email", email));

        assertThat(created.email()).isEqualTo(email);
        assertThat(created.role()).isEqualTo(UserRole.NORMAL);
        assertThat(created.deleted()).isFalse();
        assertThat(created.usernameEpithet()).isNotBlank();
        assertThat(created.usernameSuffix()).isNotBlank();
        assertThat(users().findByProvider(AuthProviderType.GOOGLE, providerId))
                .map(UserSummary::id).hasValue(created.id());
    }

    @Test
    default void findOrCreateUser_WhenTheProviderIdBelongsToADeletedUser_ReturnsThatUserStillDeleted() {
        User deleted = persisted(deletedUser().build());

        UserSummary found = users().findOrCreateUser("google",
                Map.of("id", deleted.getProviderId(), "email", deleted.getEmail()));

        assertThat(found.id()).isEqualTo(deleted.getId());
        assertThat(found.deleted()).isTrue();
    }

    @Test
    default void describeById_WhenTheUserIsUnrestricted_OmitsTheRestrictionFields() {
        User user = persisted(newUser().build());

        UserResponse response = users().describeById(user.getId());

        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.usernameEpithet()).isEqualTo(user.getUsernameEpithet());
        assertThat(response.usernameSuffix()).isEqualTo(user.getUsernameSuffix());
        assertThat(response.role()).isEqualTo(UserRole.NORMAL.name());
        assertThat(response.isBanned()).isNull();
        assertThat(response.isTimedOut()).isNull();
    }

    @Test
    default void describeById_WhenTheUserIsBannedAndTimedOut_ReportsBothWithoutARecordedReason() {
        User user = persisted(restrictedUser().build());

        UserResponse response = users().describeById(user.getId());

        assertThat(response.isBanned()).isTrue();
        assertThat(response.bannedAt()).isNotNull();
        assertThat(response.banReason()).isNull();
        assertThat(response.isTimedOut()).isTrue();
        assertThat(response.timeoutUntil()).isNotNull();
        assertThat(response.timeoutReason()).isNull();
    }

    @Test
    default void describeById_WhenTheIdIsUnknown_ThrowsUserNotFound() {
        assertThatThrownBy(() -> users().describeById(UNKNOWN_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + UNKNOWN_ID);
    }

    @Test
    default void reactivateAccount_WhenTheUserIsDeleted_MakesItActiveAgain() {
        User user = persisted(deletedUser().build());

        users().reactivateAccount(user.getId());

        assertThat(users().findActiveById(user.getId())).hasValueSatisfying(
                summary -> assertThat(summary.deleted()).isFalse());
    }

    @Test
    default void reactivateAccount_WhenTheIdIsUnknown_ThrowsUserNotFound() {
        assertThatThrownBy(() -> users().reactivateAccount(UNKNOWN_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + UNKNOWN_ID);
    }

    @Test
    default void changeRole_WhenTheUserIsLive_StoresTheNewRole() {
        User user = persisted(newUser().build());

        UserSummary changed = inTransaction(() -> users().changeRole(user.getId(), UserRole.MODERATOR));

        assertThat(changed.role()).isEqualTo(UserRole.MODERATOR);
        assertThat(users().findById(user.getId()).role()).isEqualTo(UserRole.MODERATOR);
    }

    @Test
    default void changeRole_WhenTheUserIsDeleted_ThrowsUserNotFound() {
        User user = persisted(deletedUser().build());

        assertThatThrownBy(() -> inTransaction(() -> users().changeRole(user.getId(), UserRole.MODERATOR)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id: " + user.getId());
    }

    static User.UserBuilder newUser() {
        return User.builder()
                .email("contract+" + UUID.randomUUID() + "@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(TestDataFactory.uniqueSuffix(""));
    }

    static User.UserBuilder deletedUser() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return newUser()
                .deletedAt(now.minus(Duration.ofDays(1)))
                .permanentDeleteScheduledAt(now.plus(Duration.ofDays(29)));
    }

    private static User.UserBuilder restrictedUser() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return newUser()
                .bannedAt(now.minus(Duration.ofHours(1)))
                .timeoutUntil(now.plus(Duration.ofHours(1)));
    }
}
