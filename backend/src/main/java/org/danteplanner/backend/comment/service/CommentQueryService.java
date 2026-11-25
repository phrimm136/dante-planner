package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAccessValidator.VisibleThread;
import org.danteplanner.backend.planner.api.PlannerAccess;
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
import java.util.Optional;
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
        List<PlannerComment> comments = commentRepository.findByPlannerId(plannerId);
        return switch (ThreadFill.of(comments)) {
            case EMPTY -> new ScopedRead<>(Collections.emptyList(), scopes);
            case COMMENTED -> commentedTree(comments, scopes, viewer);
        };
    }

    private enum ThreadFill {
        EMPTY,
        COMMENTED;

        static ThreadFill of(List<PlannerComment> comments) {
            return comments.isEmpty() ? EMPTY : COMMENTED;
        }
    }

    private ScopedRead<List<CommentTreeNode>> commentedTree(
            List<PlannerComment> comments, Set<FreshnessScope> scopes, Viewer viewer) {
        Set<Long> userIds = comments.stream()
                .map(PlannerComment::getUserId)
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
    public CommentTreeNode broadcastNode(PlannerComment comment, CommentTreeNode.Parent parent) {
        CommentTreeNode.Author author = users.findOptionalById(comment.getUserId())
                .<CommentTreeNode.Author>map(CommentTreeNode.Author.Known::new)
                .orElseGet(CommentTreeNode.Author.Missing::new);
        return CommentTreeNode.forBroadcast(comment, parent, author);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PlannerComment requireById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PlannerComment requireByPublicId(UUID commentPublicId) {
        return commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));
    }

    private Set<Long> upvotedIds(List<PlannerComment> comments, Viewer viewer) {
        return switch (viewer) {
            case Viewer.Guest guest -> Set.of();
            case Viewer.Member member -> new HashSet<>(commentVoteRepository.findUpvotedCommentIds(
                    comments.stream().map(PlannerComment::getId).toList(), member.userId()));
        };
    }

    private List<CommentTreeNode> buildCommentTree(
            List<PlannerComment> comments,
            Map<Long, UserSummary> userMap,
            Set<Long> upvotedIds,
            Viewer viewer
    ) {
        Map<Long, List<PlannerComment>> childrenMap = comments.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(PlannerComment::getParentCommentId));

        List<PlannerComment> topLevel = comments.stream()
                .filter(c -> c.getParentCommentId() == null)
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .toList();

        return topLevel.stream()
                .map(c -> buildNode(c, new CommentTreeNode.Parent.Thread(), childrenMap, userMap, upvotedIds, viewer))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CommentTreeNode> buildNode(
            PlannerComment comment,
            CommentTreeNode.Parent parent,
            Map<Long, List<PlannerComment>> childrenMap,
            Map<Long, UserSummary> userMap,
            Set<Long> upvotedIds,
            Viewer viewer
    ) {
        List<PlannerComment> children = childrenMap.getOrDefault(comment.getId(), Collections.emptyList());
        CommentTreeNode.Parent self = new CommentTreeNode.Parent.Comment(comment.getPublicId());
        List<CommentTreeNode> childNodes = children.stream()
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .map(c -> buildNode(c, self, childrenMap, userMap, upvotedIds, viewer))
                .flatMap(Optional::stream)
                .toList();

        if (comment.isDeleted() && childNodes.isEmpty()) {
            return Optional.empty();
        }

        UserSummary summary = userMap.get(comment.getUserId());
        CommentTreeNode.Author author = summary == null
                ? new CommentTreeNode.Author.Missing()
                : new CommentTreeNode.Author.Known(summary);
        boolean hasUpvoted = upvotedIds.contains(comment.getId());

        return Optional.of(CommentTreeNode.fromEntity(
                comment, parent, author, viewer, hasUpvoted, childNodes));
    }
}
