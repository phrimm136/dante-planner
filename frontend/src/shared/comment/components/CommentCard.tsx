import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { sanitizeUserHtml } from '@/shared/sanitize'

import { COMMENT_INDENT_PER_LEVEL } from '@/lib/constants'
import { formatCompactRelativeTime } from '@/lib/formatDate'
import { formatUsername } from '@/lib/formatUsername'
import { DeletedCommentPlaceholder } from './DeletedCommentPlaceholder'
import { CommentActionButtons } from './CommentActionButtons'
import { CommentEditor } from './CommentEditor'

import type { CommentNode } from '../types/CommentTypes'
import type { CommentActions, CommentViewer } from '../lib/commentViewer'

interface CommentCardProps {
  comment: CommentNode
  isPublished: boolean
  viewer: CommentViewer
  actions: CommentActions
  isUpvoting?: boolean
}

export const CommentCard = function CommentCard({
  comment,
  isPublished,
  viewer,
  actions,
  isUpvoting = false,
}: CommentCardProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const [showReplyEditor, setShowReplyEditor] = useState(false)
  const [showEditEditor, setShowEditEditor] = useState(false)

  const authorName =
    comment.authorEpithet && comment.authorSuffix
      ? formatUsername(comment.authorEpithet, comment.authorSuffix, i18n.language)
      : t('pages.plannerMD.comments.deletedUser')

  const formattedCreatedAt = formatCompactRelativeTime(comment.createdAt, i18n.language)

  const sanitizedContent = comment.content ? sanitizeUserHtml(comment.content) : ''

  const handleReplyClick = () => setShowReplyEditor(true)
  const handleEditClick = () => setShowEditEditor(true)

  const handleReplySubmit = (content: string) => {
    actions.onReply(comment.id, content)
    setShowReplyEditor(false)
  }

  const handleEditSubmit = (content: string) => {
    actions.onEdit(comment.id, content)
    setShowEditEditor(false)
  }

  if (comment.isDeleted) {
    return <DeletedCommentPlaceholder />
  }

  return (
    <div id={`comment-${comment.id}`} className="py-3 scroll-mt-20">
      <div className="flex items-center justify-between gap-2 mb-1">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">{authorName}</span>
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            {formattedCreatedAt}
          </span>
        </div>
        <CommentActionButtons
          comment={comment}
          isPublished={isPublished}
          viewer={viewer}
          actions={actions}
          onStartReply={handleReplyClick}
          onStartEdit={handleEditClick}
          isUpvoting={isUpvoting}
        />
      </div>

      {showEditEditor ? (
        <CommentEditor
          initialContent={comment.content ?? ''}
          onSubmit={handleEditSubmit}
          onCancel={() => setShowEditEditor(false)}
          isReply
        />
      ) : (
        <>
          {comment.updatedAt != null && (
            <span className="text-muted-foreground text-xs">
              ({t('pages.plannerMD.comments.modified', 'edited')})
            </span>
          )}
          <div
            className="prose prose-sm max-w-none text-foreground"
            dangerouslySetInnerHTML={{ __html: sanitizedContent }}
          />
        </>
      )}

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
}
