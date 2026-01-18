/**
 * CommentSection
 *
 * Main container that orchestrates the comment system:
 * - Fetches hierarchical comment tree via useCommentsQuery (Suspense)
 * - Tree is built server-side (no useCommentTree needed)
 * - Handles all mutations (create, edit, delete, vote, report, notifications)
 * - Shows empty state, loading skeleton, and new comments banner
 */

import { Suspense, useEffect, useRef, useCallback, useState } from 'react'
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
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

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

  // Shared delete confirmation dialog state
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; title: string } | null>(null)
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

  // Handlers - use refs to avoid recreating callbacks when mutation state changes
  // TanStack Query's mutate function is stable, but the mutation object reference changes
  const createCommentRef = useRef(createComment)
  const editCommentRef = useRef(editComment)
  const deleteCommentRef = useRef(deleteComment)
  const upvoteCommentRef = useRef(upvoteComment)
  const reportCommentRef = useRef(reportComment)
  const toggleNotificationsRef = useRef(toggleNotifications)

  // Keep refs updated
  createCommentRef.current = createComment
  editCommentRef.current = editComment
  deleteCommentRef.current = deleteComment
  upvoteCommentRef.current = upvoteComment
  reportCommentRef.current = reportComment
  toggleNotificationsRef.current = toggleNotifications

  // Stable handlers using refs
  const handleCreateComment = useCallback((content: string) => {
    createCommentRef.current.mutate({ plannerId, content })
  }, [plannerId])

  const handleReply = useCallback((parentCommentId: string, content: string) => {
    createCommentRef.current.mutate({ plannerId, content, parentCommentId })
  }, [plannerId])

  const handleEdit = useCallback((commentId: string, content: string) => {
    editCommentRef.current.mutate({ commentId, content, plannerId })
  }, [plannerId])

  // Opens delete confirmation dialog
  const handleDelete = useCallback((commentId: string) => {
    setDeleteTarget({ id: commentId, title: t('pages.plannerMD.comments.deleteConfirm.title') })
  }, [t])

  // Actually performs the delete after confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (deleteTarget) {
      deleteCommentRef.current.mutate({ commentId: deleteTarget.id, plannerId })
      setDeleteTarget(null)
    }
  }, [deleteTarget, plannerId])

  const handleUpvote = useCallback((commentId: string) => {
    upvoteCommentRef.current.mutate({ commentId, plannerId })
  }, [plannerId])

  const handleToggleNotifications = useCallback((commentId: string, enabled: boolean) => {
    toggleNotificationsRef.current.mutate({ commentId, enabled, plannerId })
  }, [plannerId])

  const handleReport = useCallback((commentId: string, reason: CommentReportReason) => {
    reportCommentRef.current.mutate({ commentId, reason, plannerId })
  }, [plannerId])

  const handleRefresh = useCallback(() => {
    resetCount()
    void queryClient.invalidateQueries({ queryKey: commentsQueryKeys.list(plannerId) })
  }, [resetCount, queryClient, plannerId])

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

      {/* Shared delete confirmation dialog */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('pages.plannerMD.comments.deleteConfirm.title', 'Delete comment?')}
            </DialogTitle>
            <DialogDescription>
              {t('pages.plannerMD.comments.deleteConfirm.description', 'This action cannot be undone.')}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              {t('common:cancel', 'Cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteConfirm}>
              {t('common:delete', 'Delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
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
