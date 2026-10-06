package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerQueryService;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
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

    private PlannerQueryService queryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);
        queryService = new PlannerQueryService(plannerRepository, accessGuard);

        testUser = TestDataFactory.unsavedUser(1L);
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

    private static PlannerSummaryRow summaryRow(Planner planner) {
        return summaryRow(planner, null);
    }

    private static PlannerSummaryRow summaryRow(Planner planner, Instant deletedAt) {
        return new PlannerSummaryRow() {

            @Override
            public UUID getId() {
                return planner.getId();
            }

            @Override
            public String getTitle() {
                return planner.getTitle();
            }

            @Override
            public String getCategory() {
                return planner.getCategory();
            }

            @Override
            public PlannerType getPlannerType() {
                return planner.getPlannerType();
            }

            @Override
            public PlannerStatus getStatus() {
                return planner.getStatus();
            }

            @Override
            public long getSyncVersion() {
                return planner.getSyncVersion();
            }

            @Override
            public Instant getLastModifiedAt() {
                return planner.getLastModifiedAt();
            }

            @Override
            public Instant getDeletedAt() {
                return deletedAt;
            }
        };
    }

    @Nested
    class GetPlannersTests {

        @Test
        void getPlanners_WhenCalled_ReturnsPaginatedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Planner planner = createTestPlanner();
            List<PlannerSummaryRow> rows = List.of(summaryRow(planner), summaryRow(createTestPlanner()));
            Page<PlannerSummaryRow> rowPage = new PageImpl<>(rows, pageable, 2);

            when(plannerRepository.findOwnerSummaries(testUser.getId(), pageable))
                    .thenReturn(rowPage);

            // Act
            Page<PlannerSummaryResponse> result = queryService.getPlanners(testUser.getId(), pageable, false);

            // Assert
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
            assertEquals(planner.getTitle(), result.getContent().get(0).title());
            assertEquals(planner.getSyncVersion(), result.getContent().get(0).syncVersion());
        }

        @Test
        void getPlanners_WhenIncludeDeleted_CarriesTombstones() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Instant deletedAt = Instant.parse("2026-08-16T00:00:00Z");
            Page<PlannerSummaryRow> rowPage = new PageImpl<>(
                    List.of(summaryRow(createTestPlanner(), deletedAt)), pageable, 1);

            when(plannerRepository.findOwnerSummariesIncludingDeleted(testUser.getId(), pageable))
                    .thenReturn(rowPage);

            // Act
            Page<PlannerSummaryResponse> result =
                    queryService.getPlanners(testUser.getId(), pageable, true);

            // Assert
            assertEquals(deletedAt, result.getContent().get(0).deletedAt());
        }

        @Test
        void getPlanners_WhenNoPlanners_ReturnsEmptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<PlannerSummaryRow> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findOwnerSummaries(testUser.getId(), pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PlannerSummaryResponse> result = queryService.getPlanners(testUser.getId(), pageable, false);

            // Assert
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    class GetPlannerTests {

        @Test
        void getPlanner_WhenFound_ReturnsPlanner() {
            // Arrange
            Planner planner = createTestPlanner();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act
            PlannerResponse response = queryService.getPlanner(testUser.getId(), planner.getId());

            // Assert
            assertNotNull(response);
            assertEquals(planner.getId(), response.id());
            assertEquals(planner.getTitle(), response.title());
        }

        @Test
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
