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

import { useState, useCallback, memo } from 'react'
import { useTranslation } from 'react-i18next'
import DOMPurify from 'dompurify'

import { COMMENT_INDENT_PER_LEVEL } from '@/lib/constants'
import { formatShortRelativeTime } from '@/lib/utils'
import { formatUsername } from '@/lib/formatUsername'
import { DeletedCommentPlaceholder } from './DeletedCommentPlaceholder'
import { CommentActionButtons } from './CommentActionButtons'
import { CommentEditor } from './CommentEditor'

import type { CommentNode } from '@/types/CommentTypes'

interface CommentCardProps {
  comment: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  onReply: (commentId: string, content: string) => void
  onEdit: (commentId: string, content: string) => void
  onDelete: (commentId: string) => void
  onUpvote: (commentId: string) => void
  onToggleNotifications: (commentId: string) => void
  onReport: (commentId: string) => void
  isUpvoting?: boolean
}

// Custom comparison - only re-render when comment data actually changes
function commentCardPropsAreEqual(
  prev: CommentCardProps,
  next: CommentCardProps
): boolean {
  // Compare comment data fields that affect rendering
  const prevComment = prev.comment
  const nextComment = next.comment

  return (
    prevComment.id === nextComment.id &&
    prevComment.content === nextComment.content &&
    prevComment.upvoteCount === nextComment.upvoteCount &&
    prevComment.hasUpvoted === nextComment.hasUpvoted &&
    prevComment.isDeleted === nextComment.isDeleted &&
    prevComment.isUpdated === nextComment.isUpdated &&
    prevComment.authorNotificationsEnabled === nextComment.authorNotificationsEnabled &&
    prev.isPublished === next.isPublished &&
    prev.isAuthenticated === next.isAuthenticated &&
    prev.isUpvoting === next.isUpvoting
  )
}

export const CommentCard = memo(function CommentCard({
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

  // Show deleted placeholder for deleted comments
  if (comment.isDeleted) {
    return <DeletedCommentPlaceholder />
  }

  // Format author name with i18n translation for epithet
  const authorName =
    comment.authorEpithet && comment.authorSuffix
      ? formatUsername(comment.authorEpithet, comment.authorSuffix)
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

  const handleReplyClick = useCallback(() => setShowReplyEditor(true), [])
  const handleEditClick = useCallback(() => setShowEditEditor(true), [])

  // Delete triggers parent's confirmation dialog
  const handleDeleteClick = useCallback(() => {
    onDelete(comment.id)
  }, [comment.id, onDelete])

  const handleReplySubmit = useCallback((content: string) => {
    onReply(comment.id, content)
    setShowReplyEditor(false)
  }, [comment.id, onReply])

  const handleEditSubmit = useCallback((content: string) => {
    onEdit(comment.id, content)
    setShowEditEditor(false)
  }, [comment.id, onEdit])

  const handleUpvote = useCallback(() => {
    onUpvote(comment.id)
  }, [comment.id, onUpvote])

  const handleToggleNotifications = useCallback(() => {
    onToggleNotifications(comment.id)
  }, [comment.id, onToggleNotifications])

  const handleReport = useCallback(() => {
    onReport(comment.id)
  }, [comment.id, onReport])

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
          onUpvote={handleUpvote}
          onToggleNotifications={handleToggleNotifications}
          onReport={handleReport}
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
    </div>
  )
}, commentCardPropsAreEqual)
