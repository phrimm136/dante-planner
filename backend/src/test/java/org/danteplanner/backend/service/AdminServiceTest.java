package org.danteplanner.backend.service;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.ModerationPolicy;

/**
 * Unit tests for AdminService.
 *
 * <p>Tests role change safeguards.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private ModerationActionRepository moderationActionRepository;

    private AdminService adminService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(new UsersApiAdapter(userService, null),
                new ModerationAuditService(moderationActionRepository), new ModerationPolicy());

        adminUser = User.builder()
                .id(1L)
                .publicId(java.util.UUID.randomUUID())
                .email("admin@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("admin-123")
                .usernameEpithet("ADMIN")
                .usernameSuffix("adm01")
                .role(UserRole.ADMIN)
                .build();

        moderatorUser = User.builder()
                .id(2L)
                .publicId(java.util.UUID.randomUUID())
                .email("mod@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("mod-123")
                .usernameEpithet("MOD")
                .usernameSuffix("mod01")
                .role(UserRole.MODERATOR)
                .build();

        normalUser = User.builder()
                .id(3L)
                .publicId(java.util.UUID.randomUUID())
                .email("user@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("user-123")
                .usernameEpithet("USER")
                .usernameSuffix("usr01")
                .role(UserRole.NORMAL)
                .build();
    }

    @Nested
    class ChangeRoleTests {

        @Test
        void changeRole_WhenAdminPromotesToModerator_Succeeds() {
            // Arrange - use locking query method
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.lockActiveById(normalUser.getId()))
                    .thenReturn(normalUser);

            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), normalUser.getId(), UserRole.MODERATOR);

            // Assert
            assertEquals(UserRole.MODERATOR, result.role());
        }

        @Test
        void changeRole_WhenAdminDemotesModerator_Succeeds() {
            // Arrange - use locking query method
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.lockActiveById(moderatorUser.getId()))
                    .thenReturn(moderatorUser);

            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), moderatorUser.getId(), UserRole.NORMAL);

            // Assert
            assertEquals(UserRole.NORMAL, result.role());
        }

        @Test
        void changeRole_WhenModeratorGrantsAdmin_ThrowsException() {
            // Arrange - moderator tries to grant ADMIN (use locking query method)
            when(userService.lockActiveById(moderatorUser.getId()))
                    .thenReturn(moderatorUser);
            when(userService.lockActiveById(normalUser.getId()))
                    .thenReturn(normalUser);

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), normalUser.getId(), UserRole.ADMIN)
            );
            assertTrue(exception.getMessage().contains("higher than your own"));
            assertEquals(UserRole.NORMAL, normalUser.getRole());
        }

        @Test
        void changeRole_WhenModeratorModifiesModerator_ThrowsException() {
            // Arrange - create another moderator
            User otherModerator = User.builder()
                    .id(4L)
                    .publicId(java.util.UUID.randomUUID())
                    .email("mod2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("mod2-123")
                    .usernameEpithet("MOD2")
                    .usernameSuffix("mod02")
                    .role(UserRole.MODERATOR)
                    .build();

            // Use locking query method
            when(userService.lockActiveById(moderatorUser.getId()))
                    .thenReturn(moderatorUser);
            when(userService.lockActiveById(otherModerator.getId()))
                    .thenReturn(otherModerator);

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), otherModerator.getId(), UserRole.NORMAL)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            assertEquals(UserRole.MODERATOR, otherModerator.getRole());
        }

        @Test
        void changeRole_WhenModeratorModifiesAdmin_ThrowsException() {
            // Arrange - moderator tries to modify admin (use locking query method)
            when(userService.lockActiveById(moderatorUser.getId()))
                    .thenReturn(moderatorUser);
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), adminUser.getId(), UserRole.NORMAL)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            assertEquals(UserRole.ADMIN, adminUser.getRole());
        }

        @Test
        void changeRole_WhenDemoteLastAdmin_ThrowsException() {
            // Arrange - use locking query method
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.countByRole(UserRole.ADMIN)).thenReturn(1L);

            // Act & Assert
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> adminService.changeRole(adminUser.getId(), adminUser.getId(), UserRole.MODERATOR)
            );
            assertTrue(exception.getMessage().contains("last administrator"));
            assertEquals(UserRole.ADMIN, adminUser.getRole());
        }

        @Test
        void changeRole_WhenDemotingOtherAdmin_Throws() {
            // Arrange - per spec: "Cannot modify role of users at equal or higher rank (unless self-demotion)"
            User otherAdmin = User.builder()
                    .id(5L)
                    .publicId(java.util.UUID.randomUUID())
                    .email("admin2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("admin2-123")
                    .usernameEpithet("ADMIN2")
                    .usernameSuffix("adm02")
                    .role(UserRole.ADMIN)
                    .build();

            // Use locking query method
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.lockActiveById(otherAdmin.getId()))
                    .thenReturn(otherAdmin);

            // Act & Assert - admin cannot demote another admin (only self-demotion allowed)
            ModerationForbiddenException exception = assertThrows(
                    ModerationForbiddenException.class,
                    () -> adminService.changeRole(adminUser.getId(), otherAdmin.getId(), UserRole.MODERATOR)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            assertEquals(UserRole.ADMIN, otherAdmin.getRole());
        }

        @Test
        void changeRole_WhenNonExistentActor_ThrowsUserNotFoundException() {
            // Arrange - use locking query method
            Long nonExistentId = 999L;
            when(userService.lockActiveById(nonExistentId))
                    .thenThrow(new UserNotFoundException(nonExistentId));

            // Act & Assert
            assertRejected(() -> adminService.changeRole(nonExistentId, normalUser.getId(), UserRole.MODERATOR))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void changeRole_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            // Arrange - use locking query method
            Long nonExistentId = 999L;
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.lockActiveById(nonExistentId))
                    .thenThrow(new UserNotFoundException(nonExistentId));

            // Act & Assert
            assertRejected(() -> adminService.changeRole(adminUser.getId(), nonExistentId, UserRole.MODERATOR))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void changeRole_WhenAdminSelfDemotion_Succeeds() {
            // Arrange - use locking query method
            when(userService.lockActiveById(adminUser.getId()))
                    .thenReturn(adminUser);
            when(userService.countByRole(UserRole.ADMIN)).thenReturn(2L);

            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), adminUser.getId(), UserRole.MODERATOR);

            // Assert
            assertEquals(UserRole.MODERATOR, result.role());
        }
    }

    @Nested
    class GetUserRoleTests {

        @Test
        void getUserRole_WhenUserExists_ReturnsRole() {
            // Arrange
            when(userService.findActiveById(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));

            // Act
            UserRole result = adminService.getUserRole(adminUser.getId());

            // Assert
            assertEquals(UserRole.ADMIN, result);
        }

        @Test
        void getUserRole_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userService.findActiveById(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> adminService.getUserRole(nonExistentId)).withCode("USER_NOT_FOUND");
        }
    }
}
