import { Suspense, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'

import { useAuthQuery, isStaff } from '@/shared/auth'
import { useCommentsQuery, commentsQueryKeys } from '../hooks/useCommentsQuery'
import { usePlannerCommentsSse } from '../hooks/usePlannerCommentsSse'
import {
  useCreateComment,
  useEditComment,
  useDeleteComment,
  useUpvoteComment,
  useReportComment,
  useToggleCommentNotifications,
} from '../hooks/useCommentMutations'
import { useModeratorCommentDelete } from '../hooks/useModeratorCommentDelete'
import { countComments } from '../lib/commentTree'
import { toCommentViewer } from '../lib/commentViewer'
import { CommentComposer } from './CommentComposer'
import { CommentThread } from './CommentThread'
import { NewCommentsBar } from './NewCommentsBar'
import { CommentDeleteDialog } from '@/shared/moderation'
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

import type { CommentReportReason } from '../types/CommentTypes'
import type { CommentActions } from '../lib/commentViewer'
import { SECTION_STYLES } from '@/lib/constants'

interface CommentSectionProps {
  plannerId: string
  isPublished: boolean
  isAuthenticated: boolean
}

function CommentSectionSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((i) => (
        <div key={i} className="space-y-2">
          <div className={SECTION_STYLES.LAYOUT.row}>
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-12 w-full" />
        </div>
      ))}
    </div>
  )
}

function CommentSectionContent({ plannerId, isPublished, isAuthenticated }: CommentSectionProps) {
  const { t } = useTranslation(['planner', 'common'])
  const queryClient = useQueryClient()

  const { data: currentUser } = useAuthQuery()
  const viewer = toCommentViewer(isAuthenticated, isStaff(currentUser?.role))

  const { newCommentsCount, resetCount } = usePlannerCommentsSse(plannerId)

  const tree = useCommentsQuery(plannerId)

  const [deleteTarget, setDeleteTarget] = useState<{ id: string; title: string } | null>(null)
  const [moderatorDeleteTarget, setModeratorDeleteTarget] = useState<{
    id: string
    title: string
  } | null>(null)
  const totalCount = countComments(tree)

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

      element.classList.add('bg-accent', 'transition-colors', 'duration-500')
      setTimeout(() => element.classList.remove('bg-accent'), 800)
      hasScrolled.current = true
    }
  }, [tree])

  const createComment = useCreateComment()
  const editComment = useEditComment()
  const deleteComment = useDeleteComment()
  const upvoteComment = useUpvoteComment()
  const reportComment = useReportComment()
  const toggleNotifications = useToggleCommentNotifications()
  const moderatorDeleteComment = useModeratorCommentDelete()

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
    setDeleteTarget({ id: commentId, title: t('pages.plannerMD.comments.deleteConfirm.title') })
  }

  const handleDeleteConfirm = () => {
    if (deleteTarget) {
      deleteComment.mutate({ commentId: deleteTarget.id, plannerId })
      setDeleteTarget(null)
    }
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

  const handleModeratorDelete = (commentId: string) => {
    setModeratorDeleteTarget({ id: commentId, title: '' })
  }

  const handleModeratorDeleteConfirm = (reason: string) => {
    if (moderatorDeleteTarget) {
      moderatorDeleteComment.mutate({
        commentId: moderatorDeleteTarget.id,
        plannerId,
        reason,
      })
      setModeratorDeleteTarget(null)
    }
  }

  const actions: CommentActions = {
    onReply: handleReply,
    onEdit: handleEdit,
    onDelete: handleDelete,
    onModeratorDelete: handleModeratorDelete,
    onUpvote: handleUpvote,
    onToggleNotifications: handleToggleNotifications,
    onReport: handleReport,
  }

  const handleRefresh = () => {
    resetCount()
    void queryClient.invalidateQueries({ queryKey: commentsQueryKeys.list(plannerId) })
  }

  if (!isPublished && tree.length === 0) {
    return null
  }

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>
        {t('pages.plannerMD.comments.title', 'Comments')} ({totalCount})
      </h2>

      {tree.length === 0 ? (
        <p className="text-muted-foreground text-sm py-4">
          {t('pages.plannerMD.comments.empty', 'No comments yet. Be the first to comment.')}
        </p>
      ) : (
        <div className="divide-y divide-border">
          {tree.map((node) => (
            <CommentThread
              key={node.id}
              node={node}
              isPublished={isPublished}
              viewer={viewer}
              actions={actions}
            />
          ))}
        </div>
      )}

      <NewCommentsBar count={newCommentsCount} onRefresh={handleRefresh} />

      <CommentComposer
        isPublished={isPublished}
        isAuthenticated={isAuthenticated}
        onSubmit={handleCreateComment}
        isSubmitting={createComment.isPending}
      />

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('pages.plannerMD.comments.deleteConfirm.title', 'Delete comment?')}
            </DialogTitle>
            <DialogDescription>
              {t(
                'pages.plannerMD.comments.deleteConfirm.description',
                'This action cannot be undone.',
              )}
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

      <CommentDeleteDialog
        open={moderatorDeleteTarget !== null}
        onOpenChange={(open) => !open && setModeratorDeleteTarget(null)}
        onConfirm={handleModeratorDeleteConfirm}
        isPending={moderatorDeleteComment.isPending}
      />
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
