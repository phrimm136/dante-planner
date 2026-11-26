package org.danteplanner.backend.moderation;

import org.danteplanner.backend.moderation.service.UserModerationService.RestrictedAccount;
import org.danteplanner.backend.shared.restriction.Restriction;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.ModerationPolicy;
import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for UserModerationService.
 *
 * <p>Tests the timeout and ban safeguards: who may restrict whom, and what the restriction
 * writes to the user row.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ModerationActionRepository moderationActionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserModerationService moderationService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        moderationService = new UserModerationService(new UserService(userRepository, null, null, null, null, null, Clock.systemUTC()),
                new ModerationAuditService(moderationActionRepository), eventPublisher,
                new ModerationPolicy(), Clock.systemUTC());

        adminUser = TestDataFactory.unsavedUser(UserRole.ADMIN);

        moderatorUser = TestDataFactory.unsavedUser(UserRole.MODERATOR);

        normalUser = TestDataFactory.unsavedUser(UserRole.NORMAL);
    }

    @Test
    void record_WhenTimeoutIsAudited_CarriesItsMinutes() {
        new ModerationAuditService(moderationActionRepository).record(1L, "target",
                new ModerationAuditService.Suspension.Timeout(60, "spam"));

        assertThat(persistedAction().getDurationMinutes()).isEqualTo(60);
    }

    @Test
    void record_WhenBanIsAudited_CarriesNoMinutes() {
        new ModerationAuditService(moderationActionRepository).record(1L, "target",
                new ModerationAuditService.Suspension.Ban("abuse"));

        assertThat(persistedAction().getDurationMinutes()).isNull();
    }

    private ModerationAction persistedAction() {
        ArgumentCaptor<ModerationAction> captor = ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).insert(captor.capture());
        return captor.getValue();
    }

    @Nested
    class TimeoutUserTests {

        @Test
        void timeoutUser_WhenModeratorTimeoutsNormalUser_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.timeOut(eq(normalUser.getId()), eq(normalUser.getRole()), any(), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.timeoutUntil()).isNotNull();

            assertThat(result.usernameSuffix()).isEqualTo(normalUser.getUsernameSuffix());
            assertThat(result.restriction()).isEqualTo(new Restriction.TimedOut(result.timeoutUntil()));
        }

        @Test
        void timeoutUser_WhenAdminTimeoutsNormalUser_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.timeOut(eq(normalUser.getId()), eq(normalUser.getRole()), any(), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.timeoutUser(adminUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.timeoutUntil()).isNotNull();
        }

        @Test
        void timeoutUser_WhenAdminTimeoutsModerator_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.timeOut(eq(moderatorUser.getId()), eq(moderatorUser.getRole()), any(), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.timeoutUser(adminUser.getId(), moderatorUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.timeoutUntil()).isNotNull();
        }

        @Test
        void timeoutUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = TestDataFactory.unsavedUser(UserRole.ADMIN);

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            assertRejected(() -> moderationService.timeoutUser(adminUser.getId(), targetAdmin.getId(), 60, "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot timeout a user of equal or higher rank"));
            verify(userRepository, never()).timeOut(any(), any(), any(), any());
        }

        @Test
        void timeoutUser_WhenModeratorTimeoutsModerator_ThrowsException() {
            // Arrange
            User otherModerator = TestDataFactory.unsavedUser(UserRole.MODERATOR);

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            assertRejected(() -> moderationService.timeoutUser(moderatorUser.getId(), otherModerator.getId(), 60,
                    "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot timeout a user of equal or higher rank"));
            verify(userRepository, never()).timeOut(any(), any(), any(), any());
        }

        @Test
        void timeoutUser_WhenNonExistentActor_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> moderationService.timeoutUser(nonExistentId, normalUser.getId(), 60, "Test"))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void timeoutUser_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> moderationService.timeoutUser(moderatorUser.getId(), nonExistentId, 60, "Test"))
                    .withCode("USER_NOT_FOUND");
        }
    }

    @Nested
    class RemoveTimeoutTests {

        @Test
        void removeTimeout_WhenModeratorRemovesTimeout_Succeeds() {
            // Arrange
            TestDataFactory.timeOut(normalUser, java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.clearTimeout(eq(normalUser.getId()), eq(normalUser.getRole()), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.removeTimeout(moderatorUser.getId(), normalUser.getId(), "Test clear timeout");

            // Assert
            assertThat(result.timeoutUntil()).isNull();
            assertThat(result.restriction()).isEqualTo(new Restriction.Active());
        }

        @Test
        void removeTimeout_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> moderationService.removeTimeout(moderatorUser.getId(), nonExistentId, "Test"))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void removeTimeout_WhenModeratorTargetsModerator_ThrowsException() {
            // Arrange
            User otherModerator = TestDataFactory.unsavedUser(UserRole.MODERATOR);
            TestDataFactory.timeOut(otherModerator, java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            assertRejected(() -> moderationService.removeTimeout(moderatorUser.getId(), otherModerator.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot clear the timeout of a user of equal or higher rank"));
            verify(userRepository, never()).clearTimeout(any(), any(), any());
        }

        @Test
        void removeTimeout_WhenActorIsNormalUser_ThrowsException() {
            // Arrange
            User target = TestDataFactory.unsavedUser(UserRole.NORMAL);
            TestDataFactory.timeOut(target, java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.findByIdAndDeletedAtIsNull(target.getId()))
                    .thenReturn(Optional.of(target));

            // Act & Assert
            assertRejected(() -> moderationService.removeTimeout(normalUser.getId(), target.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Only moderators can clear timeouts"));
            verify(userRepository, never()).clearTimeout(any(), any(), any());
        }
    }

    @Nested
    class BanUserTests {

        @Test
        void banUser_WhenAdminBansNormalUser_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.ban(eq(normalUser.getId()), eq(normalUser.getRole()), eq(adminUser.getId()), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.banUser(adminUser.getId(), normalUser.getId(), "Test ban reason");

            // Assert
            assertThat(result.restriction()).isInstanceOf(Restriction.Banned.class);

            ModerationAction action = persistedAction();
            assertThat(action.getActionType()).isEqualTo(ModerationActionType.BAN);
            assertThat(action.getTargetType()).isEqualTo(ModerationTargetType.USER);
            assertThat(action.getActorId()).isEqualTo(adminUser.getId());
            assertThat(action.getReason()).isEqualTo("Test ban reason");

            verify(eventPublisher).publishEvent(new AccountSuspendedEvent(
                    normalUser.getId(), "Test ban reason", SuspensionType.BAN, 0));
        }

        @Test
        void banUser_WhenAdminBansModerator_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.ban(eq(moderatorUser.getId()), eq(moderatorUser.getRole()), eq(adminUser.getId()), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.banUser(adminUser.getId(), moderatorUser.getId(), null);

            // Assert
            assertThat(result.restriction()).isInstanceOf(Restriction.Banned.class);

            ModerationAction action = persistedAction();
            assertThat(action.getActionType()).isEqualTo(ModerationActionType.BAN);
            assertThat(action.getActorId()).isEqualTo(adminUser.getId());
            assertThat(action.getReason()).isNull();
        }

        @Test
        void banUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = TestDataFactory.unsavedUser(UserRole.ADMIN);

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            assertRejected(() -> moderationService.banUser(adminUser.getId(), targetAdmin.getId(), "Reason"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot ban a user of equal or higher rank"));
            verify(userRepository, never()).ban(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any(AccountSuspendedEvent.class));
        }

        @Test
        void banUser_WhenModeratorBansUser_ThrowsException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            assertRejected(() -> moderationService.banUser(moderatorUser.getId(), normalUser.getId(), "Reason"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains("Only administrators can ban"));
            verify(userRepository, never()).ban(any(), any(), any(), any());
        }
    }

    @Nested
    class UnbanUserTests {

        @Test
        void unbanUser_WhenAdminUnbansUser_Succeeds() {
            // Arrange
            TestDataFactory.ban(normalUser, java.time.Instant.now(), adminUser.getId());

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.unban(eq(normalUser.getId()), eq(normalUser.getRole()), any())).thenReturn(1);

            // Act
            RestrictedAccount result = moderationService.unbanUser(adminUser.getId(), normalUser.getId(), "Test unban");

            // Assert
            assertThat(result.restriction()).isEqualTo(new Restriction.Active());

            ModerationAction action = persistedAction();
            assertThat(action.getActionType()).isEqualTo(ModerationActionType.UNBAN);
            assertThat(action.getTargetType()).isEqualTo(ModerationTargetType.USER);
            assertThat(action.getActorId()).isEqualTo(adminUser.getId());
        }

        @Test
        void unbanUser_WhenModeratorUnbans_ThrowsException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            assertRejected(() -> moderationService.unbanUser(moderatorUser.getId(), normalUser.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Only administrators can unban"));
        }

        @Test
        void unbanUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = TestDataFactory.unsavedUser(UserRole.ADMIN);
            TestDataFactory.ban(targetAdmin, java.time.Instant.now(), null);

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            assertRejected(() -> moderationService.unbanUser(adminUser.getId(), targetAdmin.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot unban a user of equal or higher rank"));
            verify(userRepository, never()).unban(any(), any(), any());
        }
    }
}
