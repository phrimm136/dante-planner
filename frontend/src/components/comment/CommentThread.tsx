/**
 * CommentThread
 *
 * Recursive thread renderer with depth indentation.
 * - Depth indentation: 24px per level, max 5 levels visual
 * - Border-left for nested replies
 * - Renders CommentCard for each node
 */

import { cn } from '@/lib/utils'
import {
  COMMENT_MAX_VISUAL_DEPTH_MOBILE,
  COMMENT_MAX_VISUAL_DEPTH_DESKTOP,
} from '@/lib/constants'
import { CommentCard } from './CommentCard'

import type { CommentNode, CommentReportReason } from '@/types/CommentTypes'

interface CommentThreadProps {
  node: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  plannerId: string
  onReply: (parentId: string, content: string) => void
  onEdit: (commentId: string, content: string) => void
  onDelete: (commentId: string) => void
  onUpvote: (commentId: string) => void
  onToggleNotifications: (commentId: string, enabled: boolean) => void
  onReport: (commentId: string, reason: CommentReportReason) => void
  depth?: number
}

export function CommentThread({
  node,
  isPublished,
  isAuthenticated,
  plannerId,
  onReply,
  onEdit,
  onDelete,
  onUpvote,
  onToggleNotifications,
  onReport,
  depth = 0,
}: CommentThreadProps) {
  // Responsive indent: cap at different depths for mobile vs desktop
  // Mobile: indent up to depth 3, then flatten
  // Desktop: indent up to depth 10 (backend caps at 5 anyway)
  const shouldIndentMobile = depth > 0 && depth <= COMMENT_MAX_VISUAL_DEPTH_MOBILE
  const shouldIndentDesktop = depth > 0 && depth <= COMMENT_MAX_VISUAL_DEPTH_DESKTOP

  return (
    <div
      className={cn(
        // Mobile: indent only if under mobile limit
        shouldIndentMobile ? 'ml-6' : 'ml-0',
        // Desktop: override to indent if under desktop limit
        shouldIndentDesktop && 'sm:ml-6',
        // Border for all nested comments
        depth > 0 && 'border-l-2 border-border pl-3'
      )}
    >
      <CommentCard
        comment={node}
        isPublished={isPublished}
        isAuthenticated={isAuthenticated}
        onReply={(content) => onReply(node.id, content)}
        onEdit={(content) => onEdit(node.id, content)}
        onDelete={() => onDelete(node.id)}
        onUpvote={() => onUpvote(node.id)}
        onToggleNotifications={() =>
          onToggleNotifications(node.id, !node.authorNotificationsEnabled)
        }
        onReport={() => onReport(node.id, 'OTHER')}
      />

      {/* Render replies recursively */}
      {node.replies.map((reply) => (
        <CommentThread
          key={reply.id}
          node={reply}
          isPublished={isPublished}
          isAuthenticated={isAuthenticated}
          plannerId={plannerId}
          onReply={onReply}
          onEdit={onEdit}
          onDelete={onDelete}
          onUpvote={onUpvote}
          onToggleNotifications={onToggleNotifications}
          onReport={onReport}
          depth={depth + 1}
        />
      ))}
    </div>
  )
}
