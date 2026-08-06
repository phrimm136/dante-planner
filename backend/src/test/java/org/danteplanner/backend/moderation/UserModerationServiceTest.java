package org.danteplanner.backend.moderation;

import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.auth.entity.AuthProviderType;
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
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserModerationService.
 *
 * <p>Tests the timeout and ban safeguards: who may restrict whom, and what the restriction
 * writes to the user row.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    @Mock
    private UserService userService;

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
        moderationService = new UserModerationService(userService,
                new ModerationAuditService(moderationActionRepository), eventPublisher,
                new ModerationPolicy());

        adminUser = User.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .email("admin@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("admin-123")
                .usernameEpithet("ADMIN")
                .usernameSuffix("adm01")
                .role(UserRole.ADMIN)
                .build();

        moderatorUser = User.builder()
                .id(2L)
                .publicId(UUID.randomUUID())
                .email("mod@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("mod-123")
                .usernameEpithet("MOD")
                .usernameSuffix("mod01")
                .role(UserRole.MODERATOR)
                .build();

        normalUser = User.builder()
                .id(3L)
                .publicId(UUID.randomUUID())
                .email("user@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("user-123")
                .usernameEpithet("USER")
                .usernameSuffix("usr01")
                .role(UserRole.NORMAL)
                .build();
    }

    /** The user entity handed to the repository, whose field state is what a commit would write. */
    private User persistedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        return captor.getValue();
    }

    private ModerationAction persistedAction() {
        ArgumentCaptor<ModerationAction> captor = ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("timeoutUser Tests")
    class TimeoutUserTests {

        @Test
        @DisplayName("Moderator can timeout normal user")
        void timeoutUser_WhenModeratorTimeoutsNormalUser_Succeeds() {
            // Arrange
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertNotNull(result.getTimeoutUntil());

            User persisted = persistedUser();
            assertEquals(normalUser.getId(), persisted.getId());
            assertEquals(result.getTimeoutUntil(), persisted.getTimeoutUntil());
        }

        @Test
        @DisplayName("Admin can timeout normal user")
        void timeoutUser_WhenAdminTimeoutsNormalUser_Succeeds() {
            // Arrange
            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), normalUser.getId(), 60, "Test timeout");

            // Assert
            assertNotNull(result.getTimeoutUntil());
        }

        @Test
        @DisplayName("Admin can timeout moderator")
        void timeoutUser_WhenAdminTimeoutsModerator_Succeeds() {
            // Arrange
            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), moderatorUser.getId(), 60, "Test timeout");

            // Assert
            assertNotNull(result.getTimeoutUntil());
        }

        @Test
        @DisplayName("Cannot timeout administrators")
        void timeoutUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = User.builder()
                    .id(5L)
                    .email("admin2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("admin2-123")
                    .usernameEpithet("ADMIN2")
                    .usernameSuffix("adm02")
                    .role(UserRole.ADMIN)
                    .build();

            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.timeoutUser(adminUser.getId(), targetAdmin.getId(), 60, "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot timeout a user of equal or higher rank"));
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Moderator cannot timeout other moderators")
        void timeoutUser_WhenModeratorTimeoutsModerator_ThrowsException() {
            // Arrange
            User otherModerator = User.builder()
                    .id(4L)
                    .email("mod2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("mod2-123")
                    .usernameEpithet("MOD2")
                    .usernameSuffix("mod02")
                    .role(UserRole.MODERATOR)
                    .build();

            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), otherModerator.getId(), 60, "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot timeout a user of equal or higher rank"));
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Duration must be positive - zero fails")
        void timeoutUser_WhenZeroDuration_ThrowsException() {
            // Arrange
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            InvalidRequestException exception = assertThrows(
                    InvalidRequestException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 0, "Test")
            );
            assertTrue(exception.getMessage().contains("must be positive"));
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Duration must be positive - negative fails")
        void timeoutUser_WhenNegativeDuration_ThrowsException() {
            // Arrange
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            InvalidRequestException exception = assertThrows(
                    InvalidRequestException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), -30, "Test")
            );
            assertTrue(exception.getMessage().contains("must be positive"));
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent actor")
        void timeoutUser_WhenNonExistentActor_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userService.findActiveById(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.timeoutUser(nonExistentId, normalUser.getId(), 60, "Test")
            );
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent target")
        void timeoutUser_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), nonExistentId, 60, "Test")
            );
        }
    }

    @Nested
    @DisplayName("removeTimeout Tests")
    class RemoveTimeoutTests {

        @Test
        @DisplayName("Moderator can remove timeout from user")
        void removeTimeout_WhenModeratorRemovesTimeout_Succeeds() {
            // Arrange
            normalUser.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.removeTimeout(moderatorUser.getId(), normalUser.getId(), "Test clear timeout");

            // Assert
            assertNull(result.getTimeoutUntil());

            User persisted = persistedUser();
            assertEquals(normalUser.getId(), persisted.getId());
            assertNull(persisted.getTimeoutUntil());
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent target")
        void removeTimeout_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.removeTimeout(moderatorUser.getId(), nonExistentId, "Test")
            );
        }

        @Test
        @DisplayName("Moderator cannot lift a timeout an admin placed on a peer moderator")
        void removeTimeout_WhenModeratorTargetsModerator_ThrowsException() {
            // Arrange
            User otherModerator = User.builder()
                    .id(4L)
                    .publicId(UUID.randomUUID())
                    .role(UserRole.MODERATOR)
                    .build();
            otherModerator.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.removeTimeout(moderatorUser.getId(), otherModerator.getId(), "Test")
            );
            assertTrue(exception.getMessage()
                    .contains("Cannot clear the timeout of a user of equal or higher rank"));
            assertNotNull(otherModerator.getTimeoutUntil());
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Normal user cannot clear a timeout")
        void removeTimeout_WhenActorIsNormalUser_ThrowsException() {
            // Arrange
            User target = User.builder()
                    .id(6L)
                    .publicId(UUID.randomUUID())
                    .role(UserRole.NORMAL)
                    .build();
            target.setTimeoutUntil(java.time.Instant.now().plusSeconds(3600));

            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.findActiveById(target.getId()))
                    .thenReturn(Optional.of(target));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.removeTimeout(normalUser.getId(), target.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Only moderators can clear timeouts"));
            verify(userService, never()).save(any());
        }
    }

    @Nested
    @DisplayName("banUser Tests")
    class BanUserTests {

        @Test
        @DisplayName("Admin can ban normal user")
        void banUser_WhenAdminBansNormalUser_Succeeds() {
            // Arrange
            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.banUser(adminUser.getId(), normalUser.getId(), "Test ban reason");

            // Assert
            assertTrue(result.isBanned());
            assertNotNull(result.getBannedAt());
            assertEquals(adminUser.getId(), result.getBannedBy());

            User persisted = persistedUser();
            assertEquals(normalUser.getId(), persisted.getId());
            assertTrue(persisted.isBanned());
            assertEquals(adminUser.getId(), persisted.getBannedBy());

            ModerationAction action = persistedAction();
            assertEquals(ModerationAction.ActionType.BAN, action.getActionType());
            assertEquals(ModerationAction.TargetType.USER, action.getTargetType());
            assertEquals(adminUser.getId(), action.getActorId());
            assertEquals("Test ban reason", action.getReason());

            verify(eventPublisher).publishEvent(new AccountSuspendedEvent(
                    normalUser.getId(), "Test ban reason", SuspensionType.BAN, null));
        }

        @Test
        @DisplayName("Admin can ban moderator")
        void banUser_WhenAdminBansModerator_Succeeds() {
            // Arrange
            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.banUser(adminUser.getId(), moderatorUser.getId(), null);

            // Assert
            assertTrue(result.isBanned());

            ModerationAction action = persistedAction();
            assertEquals(ModerationAction.ActionType.BAN, action.getActionType());
            assertEquals(adminUser.getId(), action.getActorId());
            assertNull(action.getReason());
        }

        @Test
        @DisplayName("Cannot ban administrators")
        void banUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = User.builder()
                    .id(5L)
                    .role(UserRole.ADMIN)
                    .build();

            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.banUser(adminUser.getId(), targetAdmin.getId(), "Reason")
            );
            assertTrue(exception.getMessage().contains("Cannot ban a user of equal or higher rank"));
            verify(userService, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any(AccountSuspendedEvent.class));
        }

        @Test
        @DisplayName("Moderator cannot ban users")
        void banUser_WhenModeratorBansUser_ThrowsException() {
            // Arrange
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.banUser(moderatorUser.getId(), normalUser.getId(), "Reason")
            );
            assertTrue(exception.getMessage().contains("Only administrators can ban"));
            verify(userService, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unbanUser Tests")
    class UnbanUserTests {

        @Test
        @DisplayName("Admin can unban user")
        void unbanUser_WhenAdminUnbansUser_Succeeds() {
            // Arrange
            normalUser.setBannedAt(java.time.Instant.now());
            normalUser.setBannedBy(adminUser.getId());

            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userService.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.unbanUser(adminUser.getId(), normalUser.getId(), "Test unban");

            // Assert
            assertFalse(result.isBanned());
            assertNull(result.getBannedAt());
            assertNull(result.getBannedBy());

            User persisted = persistedUser();
            assertEquals(normalUser.getId(), persisted.getId());
            assertFalse(persisted.isBanned());
            assertNull(persisted.getBannedBy());

            ModerationAction action = persistedAction();
            assertEquals(ModerationAction.ActionType.UNBAN, action.getActionType());
            assertEquals(ModerationAction.TargetType.USER, action.getTargetType());
            assertEquals(adminUser.getId(), action.getActorId());
        }

        @Test
        @DisplayName("Moderator cannot unban user")
        void unbanUser_WhenModeratorUnbans_ThrowsException() {
            // Arrange
            when(userService.findActiveById(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userService.findActiveById(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.unbanUser(moderatorUser.getId(), normalUser.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Only administrators can unban"));
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("Cannot unban administrators")
        void unbanUser_WhenTargetIsAdmin_ThrowsException() {
            // Arrange
            User targetAdmin = User.builder()
                    .id(5L)
                    .publicId(UUID.randomUUID())
                    .role(UserRole.ADMIN)
                    .build();
            targetAdmin.setBannedAt(java.time.Instant.now());

            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userService.findActiveById(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> moderationService.unbanUser(adminUser.getId(), targetAdmin.getId(), "Test")
            );
            assertTrue(exception.getMessage().contains("Cannot unban a user of equal or higher rank"));
            assertTrue(targetAdmin.isBanned());
            verify(userService, never()).save(any());
        }
    }
}
