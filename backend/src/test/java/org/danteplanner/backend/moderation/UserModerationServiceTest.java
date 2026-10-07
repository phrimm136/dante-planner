package org.danteplanner.backend.moderation;

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
        moderationService = new UserModerationService(new UserService(userRepository, null, null, null, null, null),
                new ModerationAuditService(moderationActionRepository), eventPublisher,
                new ModerationPolicy());

        adminUser = TestDataFactory.unsavedUser(UserRole.ADMIN);

        moderatorUser = TestDataFactory.unsavedUser(UserRole.MODERATOR);

        normalUser = TestDataFactory.unsavedUser(UserRole.NORMAL);
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

            // Act
            User result = moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.getTimeoutUntil()).isNotNull();

            // The loaded entity carries the change dirty checking would write at commit.
            assertThat(result).isSameAs(normalUser);
        }

        @Test
        void timeoutUser_WhenAdminTimeoutsNormalUser_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.getTimeoutUntil()).isNotNull();
        }

        @Test
        void timeoutUser_WhenAdminTimeoutsModerator_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), moderatorUser.getId(), 60, "Test timeout");

            // Assert
            assertThat(result.getTimeoutUntil()).isNotNull();
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
            assertThat(targetAdmin.getTimeoutUntil()).isNull();
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
            assertThat(otherModerator.getTimeoutUntil()).isNull();
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
            normalUser.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act
            User result = moderationService.removeTimeout(moderatorUser.getId(), normalUser.getId(), "Test clear timeout");

            // Assert
            assertThat(result.getTimeoutUntil()).isNull();

            assertThat(result).isSameAs(normalUser);
            assertThat(normalUser.getTimeoutUntil()).isNull();
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
            otherModerator.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            assertRejected(() -> moderationService.removeTimeout(moderatorUser.getId(), otherModerator.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot clear the timeout of a user of equal or higher rank"));
            assertThat(otherModerator.getTimeoutUntil()).isNotNull();
        }

        @Test
        void removeTimeout_WhenActorIsNormalUser_ThrowsException() {
            // Arrange
            User target = TestDataFactory.unsavedUser(UserRole.NORMAL);
            target.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.findByIdAndDeletedAtIsNull(target.getId()))
                    .thenReturn(Optional.of(target));

            // Act & Assert
            assertRejected(() -> moderationService.removeTimeout(normalUser.getId(), target.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Only moderators can clear timeouts"));
            assertThat(target.getTimeoutUntil()).isNotNull();
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

            // Act
            User result = moderationService.banUser(adminUser.getId(), normalUser.getId(), "Test ban reason");

            // Assert
            assertThat(result.isBanned()).isTrue();
            assertThat(result.getBannedAt()).isNotNull();
            assertThat(result.getBannedBy()).isEqualTo(adminUser.getId());

            assertThat(result).isSameAs(normalUser);
            assertThat(normalUser.isBanned()).isTrue();
            assertThat(normalUser.getBannedBy()).isEqualTo(adminUser.getId());

            ModerationAction action = persistedAction();
            assertThat(action.getActionType()).isEqualTo(ModerationActionType.BAN);
            assertThat(action.getTargetType()).isEqualTo(ModerationTargetType.USER);
            assertThat(action.getActorId()).isEqualTo(adminUser.getId());
            assertThat(action.getReason()).isEqualTo("Test ban reason");

            verify(eventPublisher).publishEvent(new AccountSuspendedEvent(
                    normalUser.getId(), "Test ban reason", SuspensionType.BAN, null));
        }

        @Test
        void banUser_WhenAdminBansModerator_Succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));

            // Act
            User result = moderationService.banUser(adminUser.getId(), moderatorUser.getId(), null);

            // Assert
            assertThat(result.isBanned()).isTrue();

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
            assertThat(targetAdmin.isBanned()).isFalse();
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
            assertThat(normalUser.isBanned()).isFalse();
        }
    }

    @Nested
    class UnbanUserTests {

        @Test
        void unbanUser_WhenAdminUnbansUser_Succeeds() {
            // Arrange
            normalUser.setBannedAt(java.time.Instant.now());
            normalUser.setBannedBy(adminUser.getId());

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act
            User result = moderationService.unbanUser(adminUser.getId(), normalUser.getId(), "Test unban");

            // Assert
            assertThat(result.isBanned()).isFalse();
            assertThat(result.getBannedAt()).isNull();
            assertThat(result.getBannedBy()).isNull();

            assertThat(result).isSameAs(normalUser);
            assertThat(normalUser.isBanned()).isFalse();
            assertThat(normalUser.getBannedBy()).isNull();

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
            targetAdmin.setBannedAt(java.time.Instant.now());

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            assertRejected(() -> moderationService.unbanUser(adminUser.getId(), targetAdmin.getId(), "Test"))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains("Cannot unban a user of equal or higher rank"));
            assertThat(targetAdmin.isBanned()).isTrue();
        }
    }
}
