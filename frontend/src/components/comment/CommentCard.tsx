/**
 * CommentCard
 *
 * Single comment display with arca.live-inspired layout:
 * - Row 1: Author name + date + action buttons
 * - Row 2: Content (or edit editor)
 * - Row 3: Reply editor (inline, when active)
 *
 * Shows DeletedCommentPlaceholder for deleted comments.
 * Content is sanitized with DOMPurify before rendering.
 */

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import DOMPurify from 'dompurify'

import { COMMENT_INDENT_PER_LEVEL } from '@/lib/constants'
import { formatShortRelativeTime } from '@/lib/utils'
import { formatUsername } from '@/lib/formatUsername'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { DeletedCommentPlaceholder } from './DeletedCommentPlaceholder'
import { CommentActionButtons } from './CommentActionButtons'
import { CommentEditor } from './CommentEditor'

import type { CommentNode } from '@/types/CommentTypes'

interface CommentCardProps {
  comment: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  onReply: (content: string) => void
  onEdit: (content: string) => void
  onDelete: () => void
  onUpvote: () => void
  onToggleNotifications: () => void
  onReport: () => void
  isUpvoting?: boolean
}

export function CommentCard({
  comment,
  isPublished,
  isAuthenticated,
  onReply,
  onEdit,
  onDelete,
  onUpvote,
  onToggleNotifications,
  onReport,
  isUpvoting = false,
}: CommentCardProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const [showReplyEditor, setShowReplyEditor] = useState(false)
  const [showEditEditor, setShowEditEditor] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  // Show deleted placeholder for deleted comments
  if (comment.isDeleted) {
    return <DeletedCommentPlaceholder />
  }

  // Format author name with i18n translation for association
  const authorName =
    comment.authorAssoc && comment.authorSuffix
      ? formatUsername(comment.authorAssoc, comment.authorSuffix)
      : t('pages.plannerMD.comments.deletedUser')

  // Format relative dates (short format with i18n)
  const formattedCreatedAt = formatShortRelativeTime(comment.createdAt, i18n.language)
  const formattedUpdatedAt = comment.updatedAt
    ? formatShortRelativeTime(comment.updatedAt, i18n.language)
    : null

  // Sanitize HTML content for XSS protection using DOMPurify
  const sanitizedContent = comment.content
    ? DOMPurify.sanitize(comment.content)
    : ''

  const handleReplyClick = () => setShowReplyEditor(true)
  const handleEditClick = () => setShowEditEditor(true)
  const handleDeleteClick = () => setShowDeleteConfirm(true)

  const handleReplySubmit = (content: string) => {
    onReply(content)
    setShowReplyEditor(false)
  }

  const handleEditSubmit = (content: string) => {
    onEdit(content)
    setShowEditEditor(false)
  }

  const handleDeleteConfirm = () => {
    onDelete()
    setShowDeleteConfirm(false)
  }

  return (
    <div id={`comment-${comment.id}`} className="py-3 scroll-mt-20">
      {/* Row 1: Author + date + actions */}
      <div className="flex items-center justify-between gap-2 mb-1">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">{authorName}</span>
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            {formattedCreatedAt}
            {comment.isUpdated && formattedUpdatedAt && (
              <span className="ml-1">
                ({t('pages.plannerMD.comments.updatedAt', 'updated {{date}}', { date: formattedUpdatedAt })})
              </span>
            )}
          </span>
        </div>
        <CommentActionButtons
          comment={comment}
          isPublished={isPublished}
          isAuthenticated={isAuthenticated}
          onReply={handleReplyClick}
          onEdit={handleEditClick}
          onDelete={handleDeleteClick}
          onUpvote={onUpvote}
          onToggleNotifications={onToggleNotifications}
          onReport={onReport}
          isUpvoting={isUpvoting}
        />
      </div>

      {/* Row 2: Content or Edit editor */}
      {showEditEditor ? (
        <CommentEditor
          initialContent={comment.content ?? ''}
          onSubmit={handleEditSubmit}
          onCancel={() => setShowEditEditor(false)}
          isReply
        />
      ) : (
        <div
          className="prose prose-sm max-w-none text-foreground"
          dangerouslySetInnerHTML={{ __html: sanitizedContent }}
        />
      )}

      {/* Reply editor (inline, indented to match reply position) */}
      {showReplyEditor && (
        <div
          className="mt-3 border-l-2 border-border pl-3"
          style={{ marginLeft: COMMENT_INDENT_PER_LEVEL }}
        >
          <CommentEditor
            placeholder={t('pages.plannerMD.comments.replyPlaceholder', 'Write a reply...')}
            onSubmit={handleReplySubmit}
            onCancel={() => setShowReplyEditor(false)}
            isReply
          />
        </div>
      )}

      {/* Delete confirmation dialog */}
      <Dialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('pages.plannerMD.comments.deleteConfirm.title', 'Delete comment?')}
            </DialogTitle>
            <DialogDescription>
              {t(
                'pages.plannerMD.comments.deleteConfirm.description',
                'This action cannot be undone.'
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeleteConfirm(false)}>
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
