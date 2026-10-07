package org.danteplanner.backend.moderation;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
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

import static org.junit.jupiter.api.Assertions.*;
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
            assertNotNull(result.getTimeoutUntil());

            // The loaded entity carries the change dirty checking would write at commit.
            assertSame(normalUser, result);
            assertEquals(result.getTimeoutUntil(), normalUser.getTimeoutUntil());
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
            assertNotNull(result.getTimeoutUntil());
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
            assertNotNull(result.getTimeoutUntil());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.timeoutUser(adminUser.getId(), targetAdmin.getId(), 60, "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot timeout a user of equal or higher rank"));
            assertNull(targetAdmin.getTimeoutUntil());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), otherModerator.getId(), 60, "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot timeout a user of equal or higher rank"));
            assertNull(otherModerator.getTimeoutUntil());
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
            assertNull(result.getTimeoutUntil());

            assertSame(normalUser, result);
            assertNull(normalUser.getTimeoutUntil());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.removeTimeout(moderatorUser.getId(), otherModerator.getId(), "Test")
            );
            assertTrue(exception.getMessage()
                    .contains("Cannot clear the timeout of a user of equal or higher rank"));
            assertNotNull(otherModerator.getTimeoutUntil());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.removeTimeout(normalUser.getId(), target.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Only moderators can clear timeouts"));
            assertNotNull(target.getTimeoutUntil());
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
            assertTrue(result.isBanned());
            assertNotNull(result.getBannedAt());
            assertEquals(adminUser.getId(), result.getBannedBy());

            assertSame(normalUser, result);
            assertTrue(normalUser.isBanned());
            assertEquals(adminUser.getId(), normalUser.getBannedBy());

            ModerationAction action = persistedAction();
            assertEquals(ModerationActionType.BAN, action.getActionType());
            assertEquals(ModerationTargetType.USER, action.getTargetType());
            assertEquals(adminUser.getId(), action.getActorId());
            assertEquals("Test ban reason", action.getReason());

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
            assertTrue(result.isBanned());

            ModerationAction action = persistedAction();
            assertEquals(ModerationActionType.BAN, action.getActionType());
            assertEquals(adminUser.getId(), action.getActorId());
            assertNull(action.getReason());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.banUser(adminUser.getId(), targetAdmin.getId(), "Reason")
            );
            assertTrue(exception.getMessage().contains("Cannot ban a user of equal or higher rank"));
            assertFalse(targetAdmin.isBanned());
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.banUser(moderatorUser.getId(), normalUser.getId(), "Reason")
            );
            assertTrue(exception.getMessage().contains("Only administrators can ban"));
            assertFalse(normalUser.isBanned());
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
            assertFalse(result.isBanned());
            assertNull(result.getBannedAt());
            assertNull(result.getBannedBy());

            assertSame(normalUser, result);
            assertFalse(normalUser.isBanned());
            assertNull(normalUser.getBannedBy());

            ModerationAction action = persistedAction();
            assertEquals(ModerationActionType.UNBAN, action.getActionType());
            assertEquals(ModerationTargetType.USER, action.getTargetType());
            assertEquals(adminUser.getId(), action.getActorId());
        }

        @Test
        void unbanUser_WhenModeratorUnbans_ThrowsException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.unbanUser(moderatorUser.getId(), normalUser.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Only administrators can unban"));
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
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.unbanUser(adminUser.getId(), targetAdmin.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot unban a user of equal or higher rank"));
            assertTrue(targetAdmin.isBanned());
        }
    }
}
