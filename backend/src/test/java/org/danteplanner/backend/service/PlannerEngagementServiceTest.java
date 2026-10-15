package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.mockito.ArgumentCaptor;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerModerationTargetAdapter;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.planner.precondition.VoteUniquenessValidator;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.entity.VoteType;

/**
 * Unit tests for PlannerEngagementService (immutable voting).
 */
@ExtendWith(MockitoExtension.class)
class PlannerEngagementServiceTest {

    private static final int RECOMMENDED_THRESHOLD = 10;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerReportRepository reportRepository;

    private PlannerEngagementService engagementService;

    private User testUser;

    @BeforeEach
    void setUp() {
        FakeUsers users = new FakeUsers();
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(users, plannerRepository);

        engagementService = new PlannerEngagementService(
                plannerVoteRepository,
                new PlannerStatsService(statsRepository),
                new PlannerCatalogService(catalogRepository, null, null, null, null, null, null, null,
                        RECOMMENDED_THRESHOLD),
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                accessGuard,
                new PlannerReportService(reportRepository,
                        new PlannerModerationTargetAdapter(null, accessGuard), new ReportUniquenessValidator()),
                new VoteUniquenessValidator(),
                RECOMMENDED_THRESHOLD
        );

        testUser = users.add(TestDataFactory.unsavedUser(1L));
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

    @Nested
    class CastVoteImmutabilityTests {

        private Planner createPublishedPlanner() {
            return testPlannerBuilder().published(true).build();
        }

        @Test
        void castVote_WhenDuplicateVote_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerVoteId voteId =
                new PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(testUser))));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(true);

            // Act & Assert
            assertRejected(() -> engagementService.castVote(testUser.getId(), plannerId, VoteType.UP))
                    .withCode("VOTE_ALREADY_EXISTS")
                    .isInstanceOfSatisfying(VoteAlreadyExistsException.class, exception -> {
                        assertThat(exception.getPlannerId()).isEqualTo(plannerId);
                        assertThat(exception.getUserId()).isEqualTo(testUser.getId());
                    });
            verify(plannerVoteRepository, never()).insert(any());
            verify(statsRepository, never()).incrementUpvotes(any());
        }

        @Test
        void castVote_WhenFirstVote_CreatesVote() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerVoteId voteId =
                new PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(testUser))));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            AtomicInteger upvotes = new AtomicInteger(5);
            doAnswer(invocation -> upvotes.incrementAndGet()).when(statsRepository).incrementUpvotes(plannerId);
            when(statsRepository.upvotesOf(plannerId)).thenAnswer(invocation -> upvotes.get());

            // Act
            VoteResponse response =
                    engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            // Assert
            assertThat(response.upvoteCount()).isEqualTo(6);
            assertThat(response.hasUpvoted()).isTrue();
            ArgumentCaptor<PlannerVote> voteCaptor =
                    ArgumentCaptor.forClass(PlannerVote.class);
            verify(plannerVoteRepository).insert(voteCaptor.capture());
            assertThat(voteCaptor.getValue().getUserId()).isEqualTo(testUser.getId());
            assertThat(voteCaptor.getValue().getPlannerId()).isEqualTo(plannerId);
            assertThat(voteCaptor.getValue().getVoteType()).isEqualTo(VoteType.UP);
        }

        @Test
        void castVote_WhenCrossingTheThreshold_RecordsTheRecommendedEvent() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(testUser))));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statsRepository.upvotesOf(plannerId)).thenReturn(RECOMMENDED_THRESHOLD);
            when(statsRepository.trySetRecommendedNotified(plannerId, RECOMMENDED_THRESHOLD))
                    .thenReturn(1);
            when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(invocation -> {
                DomainEvent row = invocation.getArgument(0);
                ReflectionTestUtils.setField(row, "id", 1L);
                return row;
            });

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            ArgumentCaptor<DomainEvent> recorded = ArgumentCaptor.forClass(DomainEvent.class);
            verify(domainEventRepository).insert(recorded.capture());
            assertThat(recorded.getValue().getEventType()).isEqualTo(DomainEventType.PLANNER_RECOMMENDED);
            assertThat(recorded.getValue().getAggregateId()).isEqualTo(plannerId);
            assertThat(recorded.getValue().getPayload()).isEqualTo("{\"ownerId\":" + planner.getUserId() + "}");
        }

        @Test
        void castVote_WhenAnotherVoteAlreadyLatched_RecordsNothing() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(testUser))));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statsRepository.upvotesOf(plannerId)).thenReturn(RECOMMENDED_THRESHOLD);
            when(statsRepository.trySetRecommendedNotified(plannerId, RECOMMENDED_THRESHOLD))
                    .thenReturn(0);

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            verifyNoInteractions(domainEventRepository);
        }

        @Test
        void castVote_WhenBelowTheThreshold_RecordsNothing() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(testUser))));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statsRepository.upvotesOf(plannerId)).thenReturn(RECOMMENDED_THRESHOLD - 1);

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            verifyNoInteractions(domainEventRepository);
        }
    }

    @Test
    void reportPlanner_WhenCalled_CreatesTheReportForTheReporterAndPlanner() {
        UUID plannerId = UUID.randomUUID();
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);

        engagementService.reportPlanner(testUser.getId(), plannerId);

        ArgumentCaptor<PlannerReport> report = ArgumentCaptor.forClass(PlannerReport.class);
        verify(reportRepository).insert(report.capture());
        assertThat(report.getValue().getUserId()).isEqualTo(testUser.getId());
        assertThat(report.getValue().getPlannerId()).isEqualTo(plannerId);
    }
}
