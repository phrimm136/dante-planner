package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.stereotype.Service;
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
    private final UserService userService;
    private final PlannerAccessGuard accessGuard;

    /**
     * Get comments for a planner as a hierarchical tree.
     * Tree is built server-side with deleted comments without children pruned.
     *
     * @param plannerId     the planner ID
     * @param currentUserId the current user ID (null if unauthenticated)
     * @return hierarchical tree of comments
     * @throws PlannerNotFoundException if planner not found
     * @throws CommentForbiddenException if unpublished planner and not owner
     */
    @Transactional(readOnly = true)
    public List<CommentTreeNode> getCommentTree(UUID plannerId, Long currentUserId) {
        Planner planner = accessGuard.requireExisting(plannerId);

        // Check access: published planners are public, unpublished only for owner
        if (!planner.getPublished() && (currentUserId == null || !planner.isOwnedBy(currentUserId))) {
            throw new CommentForbiddenException("Cannot view comments on unpublished planner");
        }

        List<PlannerComment> comments = commentRepository.findByPlannerId(plannerId);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = comments.stream()
                .map(PlannerComment::getUserId)
                .filter(id -> !UserAccountLifecycleService.SENTINEL_USER_ID.equals(id))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // Batch load vote status for authenticated user
        Set<Long> upvotedIds = Collections.emptySet();
        if (currentUserId != null) {
            List<Long> commentIds = comments.stream()
                    .map(PlannerComment::getId)
                    .toList();
            upvotedIds = new HashSet<>(commentVoteRepository.findUpvotedCommentIds(commentIds, currentUserId));
        }

        // Build tree structure
        return buildCommentTree(comments, userMap, upvotedIds, currentUserId);
    }

    /**
     * Require the comment carrying an internal id.
     *
     * @param commentId the comment's internal ID
     * @return the comment
     * @throws CommentNotFoundException if comment not found
     */
    @Transactional(readOnly = true)
    public PlannerComment requireById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    /**
     * Require the comment carrying a public UUID, the id every API surface exposes.
     *
     * @param commentPublicId the comment's public UUID
     * @return the comment
     * @throws CommentNotFoundException if comment not found
     */
    @Transactional(readOnly = true)
    public PlannerComment requireByPublicId(UUID commentPublicId) {
        return commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));
    }

    /**
     * Build hierarchical comment tree from flat list.
     * Prunes deleted comments without children.
     * Sorts by createdAt ascending (oldest first).
     */
    private List<CommentTreeNode> buildCommentTree(
            List<PlannerComment> comments,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        // Step 1: Group by parentId
        Map<Long, List<PlannerComment>> childrenMap = comments.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(PlannerComment::getParentCommentId));

        // Step 2: Get top-level comments (no parent)
        List<PlannerComment> topLevel = comments.stream()
                .filter(c -> c.getParentCommentId() == null)
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .toList();

        // Step 3: Recursively build tree nodes with pruning
        return topLevel.stream()
                .map(c -> buildNode(c, null, childrenMap, userMap, upvotedIds, currentUserId))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Recursively build a comment node.
     * Empty if deleted AND has no children (pruned).
     */
    private Optional<CommentTreeNode> buildNode(
            PlannerComment comment,
            UUID parentPublicId,
            Map<Long, List<PlannerComment>> childrenMap,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        // Recursively build children first
        List<PlannerComment> children = childrenMap.getOrDefault(comment.getId(), Collections.emptyList());
        List<CommentTreeNode> childNodes = children.stream()
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .map(c -> buildNode(c, comment.getPublicId(), childrenMap, userMap, upvotedIds, currentUserId))
                .flatMap(Optional::stream)
                .toList();

        if (comment.isDeleted() && childNodes.isEmpty()) {
            return Optional.empty();
        }

        // Build node
        User author = userMap.get(comment.getUserId());
        boolean hasUpvoted = upvotedIds.contains(comment.getId());

        return Optional.of(CommentTreeNode.fromEntity(
                comment, parentPublicId, author, currentUserId, hasUpvoted, childNodes));
    }
}
