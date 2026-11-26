package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAccessValidator.VisibleThread;
import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads comments: the threaded view a planner page renders, and the lookups other services resolve
 * an id through.
 */
@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private final PlannerCommentRepository commentRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final Users users;
    private final PlannerAccess plannerAccess;
    private final CommentAccessValidator accessValidator;

    @Transactional(readOnly = true)
    public ScopedRead<List<CommentTreeNode>> getCommentTree(UUID plannerId, Viewer viewer) {
        VisibleThread thread = accessValidator.requireThreadVisible(plannerAccess.requireExisting(plannerId), viewer);

        Set<FreshnessScope> scopes = Set.of(
                new FreshnessScope.Planner(plannerId), new FreshnessScope.User(thread.planner().ownerId()));
        List<CommentRow> comments = commentRepository.findByPlannerId(plannerId);
        return switch (ThreadFill.of(comments)) {
            case EMPTY -> new ScopedRead<>(Collections.emptyList(), scopes);
            case COMMENTED -> commentedTree(comments, scopes, viewer);
        };
    }

    private enum ThreadFill {
        EMPTY,
        COMMENTED;

        static ThreadFill of(List<CommentRow> comments) {
            return comments.isEmpty() ? EMPTY : COMMENTED;
        }
    }

    private ScopedRead<List<CommentTreeNode>> commentedTree(
            List<CommentRow> comments, Set<FreshnessScope> scopes, Viewer viewer) {
        Set<Long> userIds = comments.stream()
                .map(CommentRow::userId)
                .filter(id -> !Users.SENTINEL_USER_ID.equals(id))
                .collect(Collectors.toSet());
        Map<Long, UserSummary> userMap = users.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Set<FreshnessScope> threadScopes = new HashSet<>(scopes);
        userIds.forEach(id -> threadScopes.add(new FreshnessScope.User(id)));

        Set<Long> upvotedIds = upvotedIds(comments, viewer);

        return new ScopedRead<>(buildCommentTree(comments, userMap, upvotedIds, viewer), threadScopes);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public CommentTreeNode broadcastNode(CommentRow comment, CommentTreeNode.Parent parent) {
        CommentTreeNode.Author author = users.findOptionalById(comment.userId())
                .fold(CommentTreeNode.Author.Known::new, CommentTreeNode.Author.Missing::new);
        return CommentTreeNode.forBroadcast(comment, parent, author);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public CommentRow requireById(Long commentId) {
        return commentRepository.findRowById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public CommentRow requireByPublicId(UUID commentPublicId) {
        return commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));
    }

    private Set<Long> upvotedIds(List<CommentRow> comments, Viewer viewer) {
        return switch (viewer) {
            case Viewer.Guest guest -> Set.of();
            case Viewer.Member member -> new HashSet<>(commentVoteRepository.findUpvotedCommentIds(
                    comments.stream().map(CommentRow::id).toList(), member.userId()));
        };
    }

    private List<CommentTreeNode> buildCommentTree(
            List<CommentRow> comments,
            Map<Long, UserSummary> userMap,
            Set<Long> upvotedIds,
            Viewer viewer
    ) {
        Map<Long, List<CommentRow>> childrenMap = comments.stream()
                .filter(c -> c.parentCommentId() != null)
                .collect(Collectors.groupingBy(CommentRow::parentCommentId));

        List<CommentRow> topLevel = comments.stream()
                .filter(c -> c.parentCommentId() == null)
                .sorted(Comparator.comparing(CommentRow::createdAt))
                .toList();

        return topLevel.stream()
                .map(c -> buildNode(c, new CommentTreeNode.Parent.Thread(), childrenMap, userMap, upvotedIds, viewer))
                .flatMap(Option::stream)
                .toList();
    }

    private Option<CommentTreeNode> buildNode(
            CommentRow comment,
            CommentTreeNode.Parent parent,
            Map<Long, List<CommentRow>> childrenMap,
            Map<Long, UserSummary> userMap,
            Set<Long> upvotedIds,
            Viewer viewer
    ) {
        List<CommentRow> children = childrenMap.getOrDefault(comment.id(), Collections.emptyList());
        CommentTreeNode.Parent self = new CommentTreeNode.Parent.Comment(comment.publicId());
        List<CommentTreeNode> childNodes = children.stream()
                .sorted(Comparator.comparing(CommentRow::createdAt))
                .map(c -> buildNode(c, self, childrenMap, userMap, upvotedIds, viewer))
                .flatMap(Option::stream)
                .toList();

        if (comment.isDeleted() && childNodes.isEmpty()) {
            return new Option.None<>();
        }

        UserSummary summary = userMap.get(comment.userId());
        CommentTreeNode.Author author = summary == null
                ? new CommentTreeNode.Author.Missing()
                : new CommentTreeNode.Author.Known(summary);
        CommentTreeNode.Upvote upvote = upvotedIds.contains(comment.id())
                ? CommentTreeNode.Upvote.CAST
                : CommentTreeNode.Upvote.NONE;

        return new Option.Some<>(CommentTreeNode.fromEntity(
                comment, parent, author, viewer, upvote, childNodes));
    }
}
