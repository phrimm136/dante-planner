package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerQueryService;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerQueryService (owner read operations for owned planners).
 */
@ExtendWith(MockitoExtension.class)
class PlannerQueryServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerQueryService queryService;

    private User testUser;

    @BeforeEach
    void setUp() {
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
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().size()).isEqualTo(2);
            assertThat(result.getContent().get(0).title()).isEqualTo(planner.getTitle());
            assertThat(result.getContent().get(0).syncVersion()).isEqualTo(planner.getSyncVersion());
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
            assertThat(result.getContent().get(0).deletedAt()).isEqualTo(deletedAt);
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
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
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
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(planner.getId());
            assertThat(response.title()).isEqualTo(planner.getTitle());
        }

        @Test
        void getPlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> queryService.getPlanner(testUser.getId(), plannerId))
                    .withCode("PLANNER_NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains(plannerId.toString()));
        }
    }
}
