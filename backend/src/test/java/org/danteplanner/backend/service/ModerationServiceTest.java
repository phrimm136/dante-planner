package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModerationService.
 *
 * <p>Tests timeout safeguards and planner unpublish functionality.</p>
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerRepository plannerRepository;

    private ModerationService moderationService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(userRepository, plannerRepository);

        adminUser = User.builder()
                .id(1L)
                .email("admin@example.com")
                .provider("google")
                .providerId("admin-123")
                .usernameKeyword("ADMIN")
                .usernameSuffix("adm01")
                .role(UserRole.ADMIN)
                .build();

        moderatorUser = User.builder()
                .id(2L)
                .email("mod@example.com")
                .provider("google")
                .providerId("mod-123")
                .usernameKeyword("MOD")
                .usernameSuffix("mod01")
                .role(UserRole.MODERATOR)
                .build();

        normalUser = User.builder()
                .id(3L)
                .email("user@example.com")
                .provider("google")
                .providerId("user-123")
                .usernameKeyword("USER")
                .usernameSuffix("usr01")
                .role(UserRole.NORMAL)
                .build();
    }

    @Nested
    @DisplayName("timeoutUser Tests")
    class TimeoutUserTests {

        @Test
        @DisplayName("Moderator can timeout normal user")
        void timeoutUser_moderatorTimeoutsNormalUser_succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 60);

            // Assert
            assertNotNull(result.getTimeoutUntil());
            verify(userRepository).save(normalUser);
        }

        @Test
        @DisplayName("Admin can timeout normal user")
        void timeoutUser_adminTimeoutsNormalUser_succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), normalUser.getId(), 60);

            // Assert
            assertNotNull(result.getTimeoutUntil());
        }

        @Test
        @DisplayName("Admin can timeout moderator")
        void timeoutUser_adminTimeoutsModerator_succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.timeoutUser(adminUser.getId(), moderatorUser.getId(), 60);

            // Assert
            assertNotNull(result.getTimeoutUntil());
        }

        @Test
        @DisplayName("Cannot timeout administrators")
        void timeoutUser_targetIsAdmin_throwsException() {
            // Arrange
            User targetAdmin = User.builder()
                    .id(5L)
                    .email("admin2@example.com")
                    .provider("google")
                    .providerId("admin2-123")
                    .usernameKeyword("ADMIN2")
                    .usernameSuffix("adm02")
                    .role(UserRole.ADMIN)
                    .build();

            when(userRepository.findByIdAndDeletedAtIsNull(adminUser.getId()))
                    .thenReturn(Optional.of(adminUser));
            when(userRepository.findByIdAndDeletedAtIsNull(targetAdmin.getId()))
                    .thenReturn(Optional.of(targetAdmin));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> moderationService.timeoutUser(adminUser.getId(), targetAdmin.getId(), 60)
            );
            assertTrue(exception.getMessage().contains("Cannot timeout administrators"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Moderator cannot timeout other moderators")
        void timeoutUser_moderatorTimeoutsModerator_throwsException() {
            // Arrange
            User otherModerator = User.builder()
                    .id(4L)
                    .email("mod2@example.com")
                    .provider("google")
                    .providerId("mod2-123")
                    .usernameKeyword("MOD2")
                    .usernameSuffix("mod02")
                    .role(UserRole.MODERATOR)
                    .build();

            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(otherModerator.getId()))
                    .thenReturn(Optional.of(otherModerator));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), otherModerator.getId(), 60)
            );
            assertTrue(exception.getMessage().contains("cannot timeout other moderators"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duration must be positive - zero fails")
        void timeoutUser_zeroDuration_throwsException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), 0)
            );
            assertTrue(exception.getMessage().contains("must be positive"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duration must be positive - negative fails")
        void timeoutUser_negativeDuration_throwsException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), normalUser.getId(), -30)
            );
            assertTrue(exception.getMessage().contains("must be positive"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent actor")
        void timeoutUser_nonExistentActor_throwsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.timeoutUser(nonExistentId, normalUser.getId(), 60)
            );
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent target")
        void timeoutUser_nonExistentTarget_throwsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(moderatorUser.getId()))
                    .thenReturn(Optional.of(moderatorUser));
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.timeoutUser(moderatorUser.getId(), nonExistentId, 60)
            );
        }
    }

    @Nested
    @DisplayName("removeTimeout Tests")
    class RemoveTimeoutTests {

        @Test
        @DisplayName("Moderator can remove timeout from user")
        void removeTimeout_moderatorRemovesTimeout_succeeds() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(normalUser.getId()))
                    .thenReturn(Optional.of(normalUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            User result = moderationService.removeTimeout(moderatorUser.getId(), normalUser.getId());

            // Assert
            assertNull(result.getTimeoutUntil());
            verify(userRepository).save(normalUser);
        }

        @Test
        @DisplayName("Throws UserNotFoundException for non-existent target")
        void removeTimeout_nonExistentTarget_throwsUserNotFoundException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> moderationService.removeTimeout(moderatorUser.getId(), nonExistentId)
            );
        }
    }

    @Nested
    @DisplayName("unpublishPlanner Tests")
    class UnpublishPlannerTests {

        @Test
        @DisplayName("Moderator can unpublish planner")
        void unpublishPlanner_moderatorUnpublishes_succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = Planner.builder()
                    .id(plannerId)
                    .user(normalUser)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();

            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Planner result = moderationService.unpublishPlanner(moderatorUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Admin can unpublish planner")
        void unpublishPlanner_adminUnpublishes_succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = Planner.builder()
                    .id(plannerId)
                    .user(normalUser)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();

            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Planner result = moderationService.unpublishPlanner(adminUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());
        }

        @Test
        @DisplayName("Unpublish already unpublished planner succeeds")
        void unpublishPlanner_alreadyUnpublished_succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = Planner.builder()
                    .id(plannerId)
                    .user(normalUser)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(false)
                    .build();

            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Planner result = moderationService.unpublishPlanner(moderatorUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void unpublishPlanner_nonExistentPlanner_throwsPlannerNotFoundException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> moderationService.unpublishPlanner(moderatorUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for deleted planner")
        void unpublishPlanner_deletedPlanner_throwsPlannerNotFoundException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner deletedPlanner = Planner.builder()
                    .id(plannerId)
                    .user(normalUser)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();
            deletedPlanner.softDelete();

            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(deletedPlanner));

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> moderationService.unpublishPlanner(moderatorUser.getId(), plannerId)
            );
        }
    }
}
