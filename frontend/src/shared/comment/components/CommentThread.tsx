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

import type { CommentNode } from '../types/CommentTypes'
import type { CommentActions, CommentViewer } from '../lib/commentViewer'

interface CommentThreadProps {
  node: CommentNode
  /** Whether the planner carrying the thread is published. */
  isPublished: boolean
  viewer: CommentViewer
  actions: CommentActions
  depth?: number
}

export function CommentThread({
  node,
  isPublished,
  viewer,
  actions,
  depth = 0,
}: CommentThreadProps) {
  // Responsive hierarchy collapse:
  // Mobile (<lg): fully collapse hierarchy beyond depth 5 (no indent, no border)
  // Desktop (>=lg): indent up to depth 10
  const withinMobileMax = depth > 0 && depth <= COMMENT_MAX_VISUAL_DEPTH_MOBILE
  const beyondMobileWithinDesktop =
    depth > COMMENT_MAX_VISUAL_DEPTH_MOBILE && depth <= COMMENT_MAX_VISUAL_DEPTH_DESKTOP

  return (
    <div
      className={cn(
        // Mobile (<lg): full nesting (indent + border) only within mobile depth limit
        withinMobileMax && 'ml-1 border-l-2 border-border pl-1',
        // Desktop (>=lg): restore nesting for depths beyond mobile cap
        beyondMobileWithinDesktop && 'lg:ml-3 lg:border-l-2 lg:border-border lg:pl-3',
      )}
    >
      <CommentCard comment={node} isPublished={isPublished} viewer={viewer} actions={actions} />

      {/* Render replies recursively */}
      {node.replies.map((reply) => (
        <CommentThread
          key={reply.id}
          node={reply}
          isPublished={isPublished}
          viewer={viewer}
          actions={actions}
          depth={depth + 1}
        />
      ))}
    </div>
  )
}
