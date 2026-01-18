/**
 * CommentThread
 *
 * Recursive thread renderer with depth indentation.
 * - Depth indentation: 24px per level, max 5 levels visual
 * - Border-left for nested replies
 * - Renders CommentCard for each node
 * - Memoized to prevent re-renders when sibling comments change
 */

import { memo, useCallback } from 'react'
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

// Deep compare node data to prevent re-renders from object reference changes
function nodeDataEqual(prev: CommentNode, next: CommentNode): boolean {
  if (prev.id !== next.id) return false
  if (prev.content !== next.content) return false
  if (prev.upvoteCount !== next.upvoteCount) return false
  if (prev.hasUpvoted !== next.hasUpvoted) return false
  if (prev.isDeleted !== next.isDeleted) return false
  if (prev.isUpdated !== next.isUpdated) return false
  if (prev.authorNotificationsEnabled !== next.authorNotificationsEnabled) return false
  if (prev.replies.length !== next.replies.length) return false

  // Check if any reply changed
  for (let i = 0; i < prev.replies.length; i++) {
    if (!nodeDataEqual(prev.replies[i], next.replies[i])) return false
  }

  return true
}

function commentThreadPropsAreEqual(
  prev: CommentThreadProps,
  next: CommentThreadProps
): boolean {
  return (
    nodeDataEqual(prev.node, next.node) &&
    prev.isPublished === next.isPublished &&
    prev.isAuthenticated === next.isAuthenticated &&
    prev.plannerId === next.plannerId &&
    prev.depth === next.depth
  )
}

export const CommentThread = memo(function CommentThread({
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

  // Wrap handlers that need additional logic
  const handleToggleNotifications = useCallback((commentId: string) => {
    onToggleNotifications(commentId, !node.authorNotificationsEnabled)
  }, [node.authorNotificationsEnabled, onToggleNotifications])

  const handleReport = useCallback((commentId: string) => {
    onReport(commentId, 'OTHER')
  }, [onReport])

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
        onReply={onReply}
        onEdit={onEdit}
        onDelete={onDelete}
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
}, commentThreadPropsAreEqual)
