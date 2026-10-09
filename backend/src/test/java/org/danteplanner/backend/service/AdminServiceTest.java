package org.danteplanner.backend.service;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.support.FakeUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.support.TestDataFactory;

/**
 * Unit tests for AdminService.
 *
 * <p>Tests role change safeguards.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private final FakeUsers users = new FakeUsers();

    @Mock
    private ModerationActionRepository moderationActionRepository;

    private AdminService adminService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(users, new ModerationAuditService(moderationActionRepository));

        adminUser = TestDataFactory.unsavedUser(UserRole.ADMIN);

        moderatorUser = TestDataFactory.unsavedUser(UserRole.MODERATOR);

        normalUser = TestDataFactory.unsavedUser(UserRole.NORMAL);

        users.add(adminUser);
        users.add(moderatorUser);
        users.add(normalUser);
    }

    @Nested
    class ChangeRoleTests {

        @Test
        void changeRole_WhenAdminPromotesToModerator_Succeeds() {
            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), normalUser.getId(), UserRole.MODERATOR);

            // Assert
            assertThat(result.role()).isEqualTo(UserRole.MODERATOR);
        }

        @Test
        void changeRole_WhenAdminDemotesModerator_Succeeds() {
            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), moderatorUser.getId(), UserRole.NORMAL);

            // Assert
            assertThat(result.role()).isEqualTo(UserRole.NORMAL);
        }

        @Test
        void changeRole_WhenModeratorGrantsAdmin_ThrowsException() {
            // Arrange - moderator tries to grant ADMIN

            // Act & Assert
            assertRejected(() -> adminService.changeRole(moderatorUser.getId(), normalUser.getId(), UserRole.ADMIN))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains("higher than your own"));
            assertThat(normalUser.getRole()).isEqualTo(UserRole.NORMAL);
        }

        @Test
        void changeRole_WhenModeratorModifiesModerator_ThrowsException() {
            // Arrange - create another moderator
            User otherModerator = TestDataFactory.unsavedUser(UserRole.MODERATOR);

            users.add(otherModerator);

            // Act & Assert
            assertRejected(() -> adminService.changeRole(moderatorUser.getId(), otherModerator.getId(),
                    UserRole.NORMAL))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains("equal or higher rank"));
            assertThat(otherModerator.getRole()).isEqualTo(UserRole.MODERATOR);
        }

        @Test
        void changeRole_WhenModeratorModifiesAdmin_ThrowsException() {
            // Arrange - moderator tries to modify admin

            // Act & Assert
            assertRejected(() -> adminService.changeRole(moderatorUser.getId(), adminUser.getId(), UserRole.NORMAL))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains("equal or higher rank"));
            assertThat(adminUser.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        void changeRole_WhenDemotingOtherAdmin_Throws() {
            // Arrange - per spec: "Cannot modify role of users at equal or higher rank (unless self-demotion)"
            User otherAdmin = TestDataFactory.unsavedUser(UserRole.ADMIN);

            users.add(otherAdmin);

            // Act & Assert - admin cannot demote another admin (only self-demotion allowed)
            assertRejected(() -> adminService.changeRole(adminUser.getId(), otherAdmin.getId(), UserRole.MODERATOR))
                    .withCode("FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains("equal or higher rank"));
            assertThat(otherAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        void changeRole_WhenNonExistentActor_ThrowsUserNotFoundException() {
            Long nonExistentId = 999L;

            // Act & Assert
            assertRejected(() -> adminService.changeRole(nonExistentId, normalUser.getId(), UserRole.MODERATOR))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void changeRole_WhenNonExistentTarget_ThrowsUserNotFoundException() {
            Long nonExistentId = 999L;

            // Act & Assert
            assertRejected(() -> adminService.changeRole(adminUser.getId(), nonExistentId, UserRole.MODERATOR))
                    .withCode("USER_NOT_FOUND");
        }

        @Test
        void changeRole_WhenAdminSelfDemotion_Succeeds() {
            users.add(TestDataFactory.unsavedUser(UserRole.ADMIN));

            // Act
            UserSummary result = adminService.changeRole(adminUser.getId(), adminUser.getId(), UserRole.MODERATOR);

            // Assert
            assertThat(result.role()).isEqualTo(UserRole.MODERATOR);
        }
    }

    @Nested
    class GetUserRoleTests {

        @Test
        void getUserRole_WhenUserExists_ReturnsRole() {
            // Act
            UserRole result = adminService.getUserRole(adminUser.getId());

            // Assert
            assertThat(result).isEqualTo(UserRole.ADMIN);
        }

        @Test
        void getUserRole_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;

            // Act & Assert
            assertRejected(() -> adminService.getUserRole(nonExistentId)).withCode("USER_NOT_FOUND");
        }
    }
}
