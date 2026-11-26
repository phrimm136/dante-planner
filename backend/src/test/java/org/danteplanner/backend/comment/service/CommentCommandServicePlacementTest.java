package org.danteplanner.backend.comment.service;

import org.danteplanner.backend.planner.api.Visibility;
import org.danteplanner.backend.comment.repository.PlannerCommentStatsRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentContentRepository;
import org.danteplanner.backend.support.CommentRows;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.Rejections.RejectionAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCommandServicePlacementTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID OTHER_PLANNER_ID = UUID.randomUUID();
    private static final UUID PARENT_PUBLIC_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Long USER_ID = 2L;
    private static final Long PARENT_ID = 50L;
    private static final Long GRANDPARENT_ID = 40L;
    private static final Long SAVED_ID = 101L;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentContentRepository contentRepository;

    @Mock
    private PlannerCommentStatsRepository statsRepository;

    @Mock
    private PlannerCommentVoteRepository commentVoteRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    private CommentQueryService queryService;
    private CommentCommandService service;

    @BeforeEach
    void setUp() {
        FakeUsers users = new FakeUsers();
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(users, plannerRepository);
        queryService = new CommentQueryService(commentRepository, commentVoteRepository, users, accessGuard,
                new CommentAccessValidator());
        service = new CommentCommandService(commentRepository, contentRepository, statsRepository, queryService,
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                accessGuard, new PlannerStatsService(plannerStatsRepository),
                Clock.systemUTC());
    }

    @Test
    void createComment_WhenTopLevel_StoresNoParentAndLogsTheComment() {
        givenThePlanner(true);
        givenRowsAreStored();

        List<ILoggingEvent> logged = LogCapture.during(() -> comment(null), Level.INFO, CommentCommandService.class);

        assertStored(null, 0);
        assertRecorded(DomainEventType.COMMENT_RECEIVED, "{\"commentId\":101}");
        assertThat(logged).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("User 2 created comment 101 on planner " + PLANNER_ID);
    }

    @Test
    void createComment_WhenTheParentIsAtMaximumDepth_IsPlacedBesideIt() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, GRANDPARENT_ID, CommentConstants.MAX_DEPTH);

        comment(PARENT_PUBLIC_ID);

        assertStored(GRANDPARENT_ID, CommentConstants.MAX_DEPTH);
        assertRecorded(DomainEventType.REPLY_RECEIVED, "{\"replyId\":101}");
    }

    @Test
    void createComment_WhenTheParentIsAboveMaximumDepth_IsPlacedUnderItAtMaximumDepth() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, GRANDPARENT_ID, CommentConstants.MAX_DEPTH - 1);

        comment(PARENT_PUBLIC_ID);

        assertStored(PARENT_ID, CommentConstants.MAX_DEPTH);
    }

    @Test
    void createComment_WhenAMaximumDepthParentHasNoParent_AnnouncesAComment() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, null, CommentConstants.MAX_DEPTH);

        comment(PARENT_PUBLIC_ID);

        assertStored(null, CommentConstants.MAX_DEPTH);
        assertRecorded(DomainEventType.COMMENT_RECEIVED, "{\"commentId\":101}");
    }

    @Test
    void createReply_WhenAMaximumDepthParentHasNoParent_AnnouncesAReply() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, null, CommentConstants.MAX_DEPTH);

        reply();

        assertStored(null, CommentConstants.MAX_DEPTH);
        assertRecorded(DomainEventType.REPLY_RECEIVED, "{\"replyId\":101}");
    }

    @Test
    void createReply_WhenTheParentIsAtMaximumDepth_IsPlacedBesideItAndLogsTheParent() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, GRANDPARENT_ID, CommentConstants.MAX_DEPTH);

        List<ILoggingEvent> logged = LogCapture.during(this::reply, Level.INFO, CommentCommandService.class);

        assertStored(GRANDPARENT_ID, CommentConstants.MAX_DEPTH);
        assertRecorded(DomainEventType.REPLY_RECEIVED, "{\"replyId\":101}");
        assertThat(logged).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("User 2 created reply 101 to comment 50 on planner " + PLANNER_ID);
    }

    @Test
    void createReply_WhenTheParentIsTopLevel_IsPlacedUnderIt() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenParent(PLANNER_ID, null, 0);

        reply();

        assertStored(PARENT_ID, 1);
    }

    @Test
    void createReply_WhenTheParentIsADeletedReply_IsPlacedUnderIt() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenDeletedParent(PLANNER_ID, GRANDPARENT_ID, 1);

        reply();

        assertStored(PARENT_ID, 2);
    }

    @Test
    void createComment_WhenTheParentIsADeletedReply_IsPlacedUnderIt() {
        givenThePlanner(true);
        givenRowsAreStored();
        givenDeletedParent(PLANNER_ID, GRANDPARENT_ID, 1);

        comment(PARENT_PUBLIC_ID);

        assertStored(PARENT_ID, 2);
    }

    @Test
    void createComment_WhenTheParentIsADeletedTopLevelComment_IsRefused() {
        givenThePlanner(true);
        givenDeletedParent(PLANNER_ID, null, 0);

        assertForbidden(assertRejected(() -> comment(PARENT_PUBLIC_ID)), "Cannot reply to deleted top-level comment");
    }

    @Test
    void createReply_WhenTheParentIsADeletedTopLevelComment_IsRefused() {
        givenThePlanner(true);
        givenDeletedParent(PLANNER_ID, null, 0);

        assertForbidden(assertRejected(this::reply), "Cannot reply to deleted top-level comment");
    }

    @Test
    void createComment_WhenTheParentIsDeletedAndInAnotherPlanner_IsRefusedForThePlanner() {
        givenThePlanner(true);
        givenDeletedParent(OTHER_PLANNER_ID, null, 0);

        assertForbidden(assertRejected(() -> comment(PARENT_PUBLIC_ID)),
                "Parent comment belongs to a different planner");
    }

    @Test
    void createComment_WhenThePlannerIsUnpublished_IsRefusedBeforeTheParentIsRead() {
        givenThePlanner(false);

        assertRejected(() -> comment(PARENT_PUBLIC_ID)).withCode("PLANNER_NOT_FOUND");
        verifyNoInteractions(commentRepository);
    }

    @Test
    void createReply_WhenTheParentIsMissing_IsRefusedBeforeThePlannerIsRead() {
        when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.empty());

        assertRejected(this::reply).withCode("COMMENT_NOT_FOUND");
        verifyNoInteractions(plannerRepository);
    }

    @Test
    void createReply_WhenTheParentIsDeletedAndThePlannerUnpublished_IsRefusedForThePlanner() {
        givenThePlanner(false);
        givenDeletedParent(PLANNER_ID, null, 0);

        assertRejected(this::reply).withCode("PLANNER_NOT_FOUND");
    }

    @Test
    void updateComment_WhenDeletedAndByANonAuthor_IsRefusedForTheDeletion() {
        givenDeletedParent(PLANNER_ID, null, 0);

        assertForbidden(assertRejected(() -> service.updateComment(PARENT_PUBLIC_ID, USER_ID, null)),
                "Cannot edit a deleted comment");
    }

    @Test
    void deleteComment_WhenAlreadyDeletedAndByANonAuthor_ChangesNothing() {
        givenDeletedParent(PLANNER_ID, null, 0);

        List<ILoggingEvent> logged = LogCapture.during(() -> service.deleteComment(PARENT_PUBLIC_ID, USER_ID),
                Level.INFO, CommentCommandService.class);

        assertThat(logged).isEmpty();
        verifyNoInteractions(contentRepository, plannerStatsRepository);
    }

    @Test
    void getCommentTree_WhenADeletedCommentHasALiveReply_KeepsBothWithTheirParents() {
        givenThePlanner(true);
        CommentRow root = CommentRows.deleted(row(PARENT_ID, PARENT_PUBLIC_ID, PLANNER_ID, null, 0), Instant.now());
        CommentRow reply = row(SAVED_ID, UUID.randomUUID(), PLANNER_ID, PARENT_ID, 1);
        when(commentRepository.findByPlannerId(PLANNER_ID)).thenReturn(List.of(root, reply));

        List<CommentTreeNode> tree = queryService.getCommentTree(PLANNER_ID, new Viewer.Guest()).value();

        assertThat(tree).singleElement().satisfies(node -> {
            assertThat(node.parentCommentId()).isNull();
            assertThat(node.isDeleted()).isTrue();
            assertThat(node.replies()).extracting(CommentTreeNode::parentCommentId).containsExactly(PARENT_PUBLIC_ID);
        });
    }

    private void comment(UUID parentPublicId) {
        service.createComment(PLANNER_ID, USER_ID, DEVICE_ID, new CreateCommentRequest("text", parentPublicId));
    }

    private void reply() {
        service.createReply(PARENT_PUBLIC_ID, USER_ID, DEVICE_ID, "text");
    }

    private void givenThePlanner(boolean published) {
        when(plannerRepository.findThread(PLANNER_ID))
                .thenReturn(Optional.of(new PlannerThread(PLANNER_ID, 41L, published ? Visibility.SHOWN : Visibility.HIDDEN)));
    }

    private void givenParent(UUID plannerId, Long parentCommentId, int depth) {
        givenParent(row(PARENT_ID, PARENT_PUBLIC_ID, plannerId, parentCommentId, depth));
    }

    private void givenDeletedParent(UUID plannerId, Long parentCommentId, int depth) {
        givenParent(CommentRows.deleted(
                row(PARENT_ID, PARENT_PUBLIC_ID, plannerId, parentCommentId, depth), Instant.now()));
    }

    private void givenParent(CommentRow parent) {
        when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parent));
    }

    private static CommentRow row(Long id, UUID publicId, UUID plannerId, Long parentCommentId, int depth) {
        return CommentRows.live(id, publicId, plannerId, 3L, "text", parentCommentId, depth);
    }

    private void givenRowsAreStored() {
        when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(CommentRows.insertedAs(SAVED_ID));
        when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(invocation -> {
            DomainEvent row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 1L);
            return row;
        });
    }

    private void assertStored(Long parentCommentId, int depth) {
        ArgumentCaptor<PlannerComment> stored = ArgumentCaptor.forClass(PlannerComment.class);
        verify(commentRepository).insert(stored.capture());
        assertThat(stored.getValue().getPlannerId()).isEqualTo(PLANNER_ID);
        assertThat(stored.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getValue().getParentCommentId()).isEqualTo(parentCommentId);
        assertThat(stored.getValue().getDepth()).isEqualTo(depth);
        verify(contentRepository).insertContent(SAVED_ID, "text");
        verify(statsRepository).insertStats(SAVED_ID);
    }

    private void assertRecorded(DomainEventType type, String payload) {
        ArgumentCaptor<DomainEvent> recorded = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventRepository).insert(recorded.capture());
        assertThat(recorded.getValue().getEventType()).isEqualTo(type);
        assertThat(recorded.getValue().getPayload()).isEqualTo(payload);
    }

    private static void assertForbidden(RejectionAssert rejection, String message) {
        rejection.withCode("COMMENT_FORBIDDEN")
                .satisfies(refused -> assertThat(refused.getMessage()).isEqualTo(message));
    }
}
