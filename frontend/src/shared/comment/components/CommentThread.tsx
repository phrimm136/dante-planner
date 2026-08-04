/**
 * CommentThread
 *
 * Recursive thread renderer with depth indentation.
 * - Depth indentation: 24px per level, max 5 levels on mobile (<lg), 10 on desktop
 * - Border-left for nested replies
 * - Renders CommentCard for each node
 */

import { cn } from '@/lib/utils'
import { COMMENT_MAX_VISUAL_DEPTH_MOBILE, COMMENT_MAX_VISUAL_DEPTH_DESKTOP } from '@/lib/constants'
import { CommentCard } from './CommentCard'

import type { CommentNode, CommentReportReason } from '../types/CommentTypes'

interface CommentThreadProps {
  node: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  isModerator: boolean
  onReply: (parentId: string, content: string) => void
  onEdit: (commentId: string, content: string) => void
  onDelete: (commentId: string) => void
  onModeratorDelete: (commentId: string) => void
  onUpvote: (commentId: string) => void
  onToggleNotifications: (commentId: string, enabled: boolean) => void
  onReport: (commentId: string, reason: CommentReportReason) => void
  depth?: number
}

export function CommentThread({
  node,
  isPublished,
  isAuthenticated,
  isModerator,
  onReply,
  onEdit,
  onDelete,
  onModeratorDelete,
  onUpvote,
  onToggleNotifications,
  onReport,
  depth = 0,
}: CommentThreadProps) {
  // Responsive hierarchy collapse:
  // Mobile (<lg): fully collapse hierarchy beyond depth 5 (no indent, no border)
  // Desktop (>=lg): indent up to depth 10
  const withinMobileMax = depth > 0 && depth <= COMMENT_MAX_VISUAL_DEPTH_MOBILE
  const beyondMobileWithinDesktop =
    depth > COMMENT_MAX_VISUAL_DEPTH_MOBILE && depth <= COMMENT_MAX_VISUAL_DEPTH_DESKTOP

  // Wrap handlers that need additional logic
  const handleToggleNotifications = (commentId: string) => {
    onToggleNotifications(commentId, !node.authorNotificationsEnabled)
  }

  const handleReport = (commentId: string) => {
    onReport(commentId, 'OTHER')
  }

  return (
    <div
      className={cn(
        // Mobile (<lg): full nesting (indent + border) only within mobile depth limit
        withinMobileMax && 'ml-1 border-l-2 border-border pl-1',
        // Desktop (>=lg): restore nesting for depths beyond mobile cap
        beyondMobileWithinDesktop && 'lg:ml-3 lg:border-l-2 lg:border-border lg:pl-3',
      )}
    >
      <CommentCard
        comment={node}
        isPublished={isPublished}
        isAuthenticated={isAuthenticated}
        isModerator={isModerator}
        onReply={onReply}
        onEdit={onEdit}
        onDelete={onDelete}
        onModeratorDelete={onModeratorDelete}
        onUpvote={onUpvote}
        onToggleNotifications={handleToggleNotifications}
        onReport={handleReport}
      />

      {/* Render replies recursively */}
      {node.replies.map((reply) => (
        <CommentThread
          key={reply.id}
          node={reply}
          isPublished={isPublished}
          isAuthenticated={isAuthenticated}
          isModerator={isModerator}
          onReply={onReply}
          onEdit={onEdit}
          onDelete={onDelete}
          onModeratorDelete={onModeratorDelete}
          onUpvote={onUpvote}
          onToggleNotifications={onToggleNotifications}
          onReport={onReport}
          depth={depth + 1}
        />
      ))}
    </div>
  )
}
