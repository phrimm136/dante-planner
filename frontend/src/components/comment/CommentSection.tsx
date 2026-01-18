/**
 * CommentSection
 *
 * Main container that orchestrates the comment system:
 * - Fetches hierarchical comment tree via useCommentsQuery (Suspense)
 * - Tree is built server-side (no useCommentTree needed)
 * - Handles all mutations (create, edit, delete, vote, report, notifications)
 * - Shows empty state, loading skeleton, and new comments banner
 */

import { Suspense, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'

import { useCommentsQuery, commentsQueryKeys } from '@/hooks/useCommentsQuery'
import { usePlannerCommentsSse } from '@/hooks/usePlannerCommentsSse'
import {
  useCreateComment,
  useEditComment,
  useDeleteComment,
  useUpvoteComment,
  useReportComment,
  useToggleCommentNotifications,
} from '@/hooks/useCommentMutations'
import { CommentEditor } from './CommentEditor'
import { CommentThread } from './CommentThread'
import { NewCommentsBar } from './NewCommentsBar'
import { Skeleton } from '@/components/ui/skeleton'

import type { CommentNode, CommentReportReason } from '@/types/CommentTypes'

interface CommentSectionProps {
  plannerId: string
  isPublished: boolean
  isAuthenticated: boolean
}

/** Count all comments in tree recursively */
function countComments(nodes: CommentNode[]): number {
  return nodes.reduce((acc, node) => acc + 1 + countComments(node.replies), 0)
}

function CommentSectionSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((i) => (
        <div key={i} className="space-y-2">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-12 w-full" />
        </div>
      ))}
    </div>
  )
}

function CommentSectionContent({
  plannerId,
  isPublished,
  isAuthenticated,
}: CommentSectionProps) {
  const { t } = useTranslation(['planner', 'common'])
  const queryClient = useQueryClient()

  // Real-time new comment notifications via SSE
  const { newCommentsCount, resetCount } = usePlannerCommentsSse(plannerId)

  // Fetch tree (already built server-side)
  const tree = useCommentsQuery(plannerId)
  const totalCount = countComments(tree)

  // Scroll to comment from URL hash (e.g., #comment-uuid from notification link)
  // Instant scroll - let browser scroll anchoring maintain position as content above loads
  const hasScrolled = useRef(false)
  useEffect(() => {
    if (hasScrolled.current || tree.length === 0) return
    const hash = window.location.hash
    if (!hash.startsWith('#comment-')) return

    const element = document.getElementById(hash.slice(1))
    if (element) {
      // Instant scroll to comment - browser scroll anchoring handles content loading above
      element.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'nearest' })

      // Highlight the comment briefly with fade-out transition
      element.classList.add('bg-accent', 'transition-colors', 'duration-500')
      setTimeout(() => element.classList.remove('bg-accent'), 800)
      hasScrolled.current = true
    }
  }, [tree])

  // Mutations
  const createComment = useCreateComment()
  const editComment = useEditComment()
  const deleteComment = useDeleteComment()
  const upvoteComment = useUpvoteComment()
  const reportComment = useReportComment()
  const toggleNotifications = useToggleCommentNotifications()

  // Handlers (commentId is now UUID string)
  const handleCreateComment = (content: string) => {
    createComment.mutate({ plannerId, content })
  }

  const handleReply = (parentCommentId: string, content: string) => {
    createComment.mutate({ plannerId, content, parentCommentId })
  }

  const handleEdit = (commentId: string, content: string) => {
    editComment.mutate({ commentId, content, plannerId })
  }

  const handleDelete = (commentId: string) => {
    deleteComment.mutate({ commentId, plannerId })
  }

  const handleUpvote = (commentId: string) => {
    upvoteComment.mutate({ commentId, plannerId })
  }

  const handleToggleNotifications = (commentId: string, enabled: boolean) => {
    toggleNotifications.mutate({ commentId, enabled, plannerId })
  }

  const handleReport = (commentId: string, reason: CommentReportReason) => {
    reportComment.mutate({ commentId, reason, plannerId })
  }

  const handleRefresh = () => {
    resetCount()
    void queryClient.invalidateQueries({ queryKey: commentsQueryKeys.list(plannerId) })
  }

  // Unpublished with no comments - hide section entirely
  if (!isPublished && tree.length === 0) {
    return null
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">
        {t('pages.plannerMD.comments.title', 'Comments')} ({totalCount})
      </h2>

      {/* Comment list */}
      {tree.length === 0 ? (
        <p className="text-muted-foreground text-sm py-4">
          {t(
            'pages.plannerMD.comments.empty',
            'No comments yet. Be the first to comment.'
          )}
        </p>
      ) : (
        <div className="divide-y divide-border">
          {tree.map((node) => (
            <CommentThread
              key={node.id}
              node={node}
              isPublished={isPublished}
              isAuthenticated={isAuthenticated}
              plannerId={plannerId}
              onReply={handleReply}
              onEdit={handleEdit}
              onDelete={handleDelete}
              onUpvote={handleUpvote}
              onToggleNotifications={handleToggleNotifications}
              onReport={handleReport}
            />
          ))}
        </div>
      )}

      {/* New comments banner */}
      <NewCommentsBar count={newCommentsCount} onRefresh={handleRefresh} />

      {/* Comment writer */}
      {isPublished ? (
        isAuthenticated ? (
          <CommentEditor
            placeholder={t(
              'pages.plannerMD.comments.placeholder',
              'Write a comment...'
            )}
            onSubmit={handleCreateComment}
            isSubmitting={createComment.isPending}
          />
        ) : (
          <p className="text-muted-foreground text-sm">
            {t(
              'pages.plannerMD.comments.loginRequired',
              'Sign in to leave a comment.'
            )}
          </p>
        )
      ) : (
        <p className="text-muted-foreground text-sm">
          {t(
            'pages.plannerMD.comments.unpublished',
            'Comments are disabled while this planner is unpublished.'
          )}
        </p>
      )}
    </div>
  )
}

export function CommentSection(props: CommentSectionProps) {
  return (
    <Suspense fallback={<CommentSectionSkeleton />}>
      <CommentSectionContent {...props} />
    </Suspense>
  )
}
