package org.danteplanner.backend.service;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * What a comment write owes, and nothing about who ends up hearing it.
 *
 * <p>The write records one event row keyed on the comment it created, and that is the whole of its
 * obligation. Who is eligible for a notification, and what the thread push carries, are decided by
 * the arm that reads the row — the write cannot answer them without holding the answer open across
 * a commit it might not survive.</p>
 */
@ExtendWith(MockitoExtension.class)
class CommentCommandServiceNotificationTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID PARENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long COMMENTER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentVoteRepository commentVoteRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    private final FakeUsers users = new FakeUsers();

    private CommentCommandService service;

    @BeforeEach
    void setUp() {
        service = new CommentCommandService(
                commentRepository,
                new CommentQueryService(commentRepository, commentVoteRepository, users,
                        new PlannerAccessGuard(users, plannerRepository),
                        new CommentAccessValidator()),
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                new CommentAccessValidator(), new CommentAuthorshipValidator(),
                new CommentStateValidator());
    }

    @Test
    void createComment_WhenTopLevel_RecordsOneCommentReceivedEvent() {
        givenAPublishedPlanner();
        givenEventRowsAreStored();
        when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(1L));

        service.createComment(PLANNER_ID, COMMENTER_ID, UUID.randomUUID(),
                new CreateCommentRequest("Top level", null));

        assertRecordedOnce(DomainEventType.COMMENT_RECEIVED, "{\"commentId\":1}");
    }

    @Test
    void createComment_WhenItCarriesAParent_RecordsOneReplyReceivedEvent() {
        givenAPublishedPlanner();
        givenEventRowsAreStored();
        when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parent()));
        when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(101L));

        service.createComment(PLANNER_ID, COMMENTER_ID, UUID.randomUUID(),
                new CreateCommentRequest("Reply", PARENT_PUBLIC_ID));

        assertRecordedOnce(DomainEventType.REPLY_RECEIVED, "{\"replyId\":101}");
    }

    @Test
    void createReply_WhenPosted_RecordsOneReplyReceivedEvent() {
        givenAPublishedPlanner();
        givenEventRowsAreStored();
        when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parent()));
        when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(101L));

        service.createReply(PARENT_PUBLIC_ID, COMMENTER_ID, UUID.randomUUID(), "Reply");

        assertRecordedOnce(DomainEventType.REPLY_RECEIVED, "{\"replyId\":101}");
    }

    private void assertRecordedOnce(DomainEventType type, String payload) {
        ArgumentCaptor<DomainEvent> recorded = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventRepository).insert(recorded.capture());
        verifyNoMoreInteractions(domainEventRepository);
        assertThat(recorded.getAllValues())
                .extracting(DomainEvent::getEventType, DomainEvent::getAggregateId, DomainEvent::getPayload)
                .containsExactly(tuple(type, PLANNER_ID, payload));
    }

    private void givenEventRowsAreStored() {
        when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent row = inv.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 1L);
            return row;
        });
    }

    private void givenAPublishedPlanner() {
        users.add(User.builder()
                .id(COMMENTER_ID)
                .email("commenter@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("commenter-123")
                .usernameEpithet("COMMENTER")
                .usernameSuffix("com01")
                .role(UserRole.NORMAL)
                .build());
        when(plannerRepository.existsPublishedById(PLANNER_ID)).thenReturn(true);
    }

    private static PlannerComment parent() {
        PlannerComment parent = new PlannerComment(PLANNER_ID, PARENT_AUTHOR_ID, "Parent", null, 0);
        parent.setId(50L);
        parent.setPublicId(PARENT_PUBLIC_ID);
        parent.setCreatedAt(Instant.now());
        return parent;
    }

    private static Answer<PlannerComment> savedComment(Long id) {
        return inv -> {
            PlannerComment c = inv.getArgument(0);
            c.setId(id);
            c.setPublicId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            return c;
        };
    }
}
