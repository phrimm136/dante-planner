import { cn } from '@/lib/utils'
import { COMMENT_MAX_VISUAL_DEPTH_DESKTOP, COMMENT_MAX_VISUAL_DEPTH_MOBILE } from '@/lib/constants'
import { CommentCard } from './CommentCard'

import type { CommentNode } from '../types/CommentTypes'
import type { CommentActions, CommentViewer } from '../lib/commentViewer'

interface CommentThreadProps {
  node: CommentNode
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
  const withinMobileMax = depth > 0 && depth <= COMMENT_MAX_VISUAL_DEPTH_MOBILE
  const beyondMobileWithinDesktop =
    depth > COMMENT_MAX_VISUAL_DEPTH_MOBILE && depth <= COMMENT_MAX_VISUAL_DEPTH_DESKTOP

  return (
    <div
      className={cn(
        withinMobileMax && 'ml-1 border-l-2 border-border pl-1',
        beyondMobileWithinDesktop && 'lg:ml-3 lg:border-l-2 lg:border-border lg:pl-3',
      )}
    >
      <CommentCard comment={node} isPublished={isPublished} viewer={viewer} actions={actions} />

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
