package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerQueryService;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerQueryService (owner read operations for owned planners).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerQueryServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private UserService userService;

    private PlannerQueryService queryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userService, plannerRepository);
        queryService = new PlannerQueryService(plannerRepository, plannerStatsRepository, accessGuard);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON);
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("getPlanners Tests")
    class GetPlannersTests {

        @Test
        @DisplayName("Should return paginated planners for user")
        void getPlanners_WhenCalled_ReturnsPaginatedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<PlannerSummaryResponse> summaries = List.of(
                    PlannerSummaryResponse.fromEntity(createTestPlanner()),
                    PlannerSummaryResponse.fromEntity(createTestPlanner()));
            Page<PlannerSummaryResponse> summaryPage = new PageImpl<>(summaries, pageable, 2);

            when(plannerRepository.findOwnerSummaries(testUser.getId(), pageable))
                    .thenReturn(summaryPage);

            // Act
            Page<PlannerSummaryResponse> result = queryService.getPlanners(testUser.getId(), pageable);

            // Assert
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should return empty page when no planners exist")
        void getPlanners_WhenNoPlanners_ReturnsEmptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<PlannerSummaryResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findOwnerSummaries(testUser.getId(), pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PlannerSummaryResponse> result = queryService.getPlanners(testUser.getId(), pageable);

            // Assert
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("getPlanner Tests")
    class GetPlannerTests {

        @Test
        @DisplayName("Should return planner when found")
        void getPlanner_WhenFound_ReturnsPlanner() {
            // Arrange
            Planner planner = createTestPlanner();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerStatsRepository.findById(planner.getId()))
                    .thenReturn(Optional.empty());

            // Act
            PlannerResponse response = queryService.getPlanner(testUser.getId(), planner.getId());

            // Assert
            assertNotNull(response);
            assertEquals(planner.getId(), response.id());
            assertEquals(planner.getTitle(), response.title());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when not found")
        void getPlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            PlannerNotFoundException exception = assertThrows(
                    PlannerNotFoundException.class,
                    () -> queryService.getPlanner(testUser.getId(), plannerId)
            );

            assertTrue(exception.getMessage().contains(plannerId.toString()));
        }
    }
}
