package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminService.
 *
 * <p>Tests role change safeguards and token invalidation on demotion.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AdminService adminService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, tokenBlacklistService);

        adminUser = User.builder()
                .id(1L)
                .email("admin@example.com")
                .provider("google")
                .providerId("admin-123")
                .usernameEpithet("ADMIN")
                .usernameSuffix("adm01")
                .role(UserRole.ADMIN)
                .build();

        moderatorUser = User.builder()
                .id(2L)
                .email("mod@example.com")
                .provider("google")
                .providerId("mod-123")
                .usernameEpithet("MOD")
                .usernameSuffix("mod01")
                .role(UserRole.MODERATOR)
                .build();

        normalUser = User.builder()
                .id(3L)
                .email("user@example.com")
                .provider("google")
                .providerId("user-123")
                .usernameEpithet("USER")
                .usernameSuffix("usr01")
                .role(UserRole.NORMAL)
                .build();
    }

    @Nested
    @DisplayName("changeRole Tests")
    class ChangeRoleTests {

        @Test
        @DisplayName("Admin can promote normal user to moderator")
        void changeRole_adminPromotesToModerator_succeeds() {
            // Arrange - use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = adminService.changeRole(adminUser.getId(), normalUser.getId(), UserRole.MODERATOR);

            // Assert
            assertEquals(UserRole.MODERATOR, result.getRole());
            verify(tokenBlacklistService, never()).invalidateUserTokens(anyLong());
        }

        @Test
        @DisplayName("Admin can demote moderator to normal - tokens invalidated")
        void changeRole_adminDemotesModerator_invalidatesTokens() {
            // Arrange - use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = adminService.changeRole(adminUser.getId(), moderatorUser.getId(), UserRole.NORMAL);

            // Assert
            assertEquals(UserRole.NORMAL, result.getRole());
            verify(tokenBlacklistService).invalidateUserTokens(moderatorUser.getId());
        }

        @Test
        @DisplayName("Cannot grant role higher than own")
        void changeRole_moderatorGrantsAdmin_throwsException() {
            // Arrange - moderator tries to grant ADMIN (use locking query method)
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), normalUser.getId(), UserRole.ADMIN)
            );
            assertTrue(exception.getMessage().contains("higher than your own"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cannot modify user of equal rank")
        void changeRole_moderatorModifiesModerator_throwsException() {
            // Arrange - create another moderator
            User otherModerator = User.builder()
                    .id(4L)
                    .email("mod2@example.com")
                    .provider("google")
                    .providerId("mod2-123")
                    .usernameEpithet("MOD2")
                    .usernameSuffix("mod02")
                    .role(UserRole.MODERATOR)
                    .build();

            // Use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), otherModerator.getId(), UserRole.NORMAL)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cannot modify user of higher rank")
        void changeRole_moderatorModifiesAdmin_throwsException() {
            // Arrange - moderator tries to modify admin (use locking query method)
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminService.changeRole(moderatorUser.getId(), adminUser.getId(), UserRole.NORMAL)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cannot demote last admin")
        void changeRole_demoteLastAdmin_throwsException() {
            // Arrange - use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminService.changeRole(adminUser.getId(), adminUser.getId(), UserRole.MODERATOR)
            );
            assertTrue(exception.getMessage().contains("last administrator"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cannot demote another admin even when multiple admins exist")
        void changeRole_cannotDemoteOtherAdmin() {
            // Arrange - per spec: "Cannot modify role of users at equal or higher rank (unless self-demotion)"
            User otherAdmin = User.builder()
                    .id(5L)
                    .email("admin2@example.com")
                    .provider("google")
                    .providerId("admin2-123")
                    .usernameEpithet("ADMIN2")
                    .usernameSuffix("adm02")
                    .role(UserRole.ADMIN)
                    .build();

            // Use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(otherAdmin.getId()))
                    .thenReturn(Optional.of(otherAdmin));

            // Act & Assert - admin cannot demote another admin (only self-demotion allowed)
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminService.changeRole(adminUser.getId(), otherAdmin.getId(), UserRole.MODERATOR)
            );
            assertTrue(exception.getMessage().contains("equal or higher rank"));
            verify(userRepository, never()).save(any());
            verify(tokenBlacklistService, never()).invalidateUserTokens(anyLong());
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent actor")
        void changeRole_nonExistentActor_throwsUserNotFoundException() {
            // Arrange - use locking query method
            Long nonExistentId = 999L;
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> adminService.changeRole(nonExistentId, normalUser.getId(), UserRole.MODERATOR)
            );
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent target")
        void changeRole_nonExistentTarget_throwsUserNotFoundException() {
            // Arrange - use locking query method
            Long nonExistentId = 999L;
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> adminService.changeRole(adminUser.getId(), nonExistentId, UserRole.MODERATOR)
            );
        }

        @Test
        @DisplayName("Admin can self-demote when multiple admins exist")
        void changeRole_adminSelfDemotion_succeeds() {
            // Arrange - use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = adminService.changeRole(adminUser.getId(), adminUser.getId(), UserRole.MODERATOR);

            // Assert
            assertEquals(UserRole.MODERATOR, result.getRole());
            verify(tokenBlacklistService).invalidateUserTokens(adminUser.getId());
        }

        @Test
        @DisplayName("No token invalidation on promotion")
        void changeRole_promotionNoTokenInvalidation() {
            // Arrange - use locking query method
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findWithLockByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            adminService.changeRole(adminUser.getId(), normalUser.getId(), UserRole.ADMIN);

            // Assert
            verify(tokenBlacklistService, never()).invalidateUserTokens(anyLong());
        }
    }

    @Nested
    @DisplayName("getUserRole Tests")
    class GetUserRoleTests {

        @Test
        @DisplayName("Should return user role when user exists")
        void getUserRole_userExists_returnsRole() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));

            // Act
            UserRole result = adminService.getUserRole(adminUser.getId());

            // Assert
            assertEquals(UserRole.ADMIN, result);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void getUserRole_userNotFound_throwsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> adminService.getUserRole(nonExistentId)
            );
        }
    }
}
