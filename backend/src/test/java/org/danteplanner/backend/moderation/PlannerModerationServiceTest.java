package org.danteplanner.backend.moderation;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerModerationService.
 *
 * <p>Tests the moderator-driven visibility transitions on a planner aggregate.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerModerationServiceTest {

    @Mock
    private PlannerPublishingService plannerPublishingService;

    @Mock
    private ModerationActionRepository moderationActionRepository;

    private PlannerModerationService moderationService;

    private User adminUser;
    private User moderatorUser;
    private User normalUser;

    /** The aggregate the publishing seam was handed, recorded as the seam applies the transition. */
    private Planner persisted;

    @BeforeEach
    void setUp() {
        moderationService = new PlannerModerationService(plannerPublishingService,
                new ModerationAuditService(moderationActionRepository));

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

    /** Stand the publishing seam up over one aggregate, applying whatever transition it is handed. */
    private void seamWithdraws(UUID plannerId, Planner planner) {
        when(plannerPublishingService.withdrawFromPublicView(eq(plannerId), any()))
                .thenAnswer(invocation -> {
                    invocation.<PlannerPublishingService.Withdrawal>getArgument(1).apply(planner);
                    persisted = planner;
                    return planner;
                });
    }

    /** The planner aggregate handed to the publishing seam, whose field state is what a commit would write. */
    private Planner persistedPlanner() {
        verify(plannerPublishingService).withdrawFromPublicView(any(), any());
        return persisted;
    }

    @Nested
    @DisplayName("unpublishPlanner Tests")
    class UnpublishPlannerTests {

        @Test
        @DisplayName("Moderator can unpublish planner")
        void unpublishPlanner_WhenModeratorUnpublishes_Succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = TestDataFactory.planner(normalUser)
                    .id(plannerId)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();

            seamWithdraws(plannerId, planner);

            // Act
            Planner result = moderationService.unpublishPlanner(moderatorUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());

            Planner persisted = persistedPlanner();
            assertEquals(plannerId, persisted.getId());
            assertFalse(persisted.getPublished());
        }

        @Test
        @DisplayName("Admin can unpublish planner")
        void unpublishPlanner_WhenAdminUnpublishes_Succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = TestDataFactory.planner(normalUser)
                    .id(plannerId)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();

            seamWithdraws(plannerId, planner);

            // Act
            Planner result = moderationService.unpublishPlanner(adminUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());
        }

        @Test
        @DisplayName("Unpublish already unpublished planner succeeds")
        void unpublishPlanner_WhenAlreadyUnpublished_Succeeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner planner = TestDataFactory.planner(normalUser)
                    .id(plannerId)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(false)
                    .build();

            seamWithdraws(plannerId, planner);

            // Act
            Planner result = moderationService.unpublishPlanner(moderatorUser.getId(), plannerId);

            // Assert
            assertFalse(result.getPublished());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void unpublishPlanner_WhenNonExistentPlanner_ThrowsPlannerNotFoundException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerPublishingService.withdrawFromPublicView(eq(nonExistentId), any()))
                    .thenThrow(new PlannerNotFoundException(nonExistentId));

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> moderationService.unpublishPlanner(moderatorUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for deleted planner")
        void unpublishPlanner_WhenDeletedPlanner_ThrowsPlannerNotFoundException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner deletedPlanner = TestDataFactory.planner(normalUser)
                    .id(plannerId)
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .published(true)
                    .build();
            deletedPlanner.softDelete();

            // The aggregate load behind the seam filters soft-deleted rows at the query level;
            // proving that filter holds needs a containerized test against the real schema.
            when(plannerPublishingService.withdrawFromPublicView(eq(plannerId), any()))
                    .thenThrow(new PlannerNotFoundException(plannerId));

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> moderationService.unpublishPlanner(moderatorUser.getId(), plannerId)
            );
        }
    }
}
