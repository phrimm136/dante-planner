/**
 * CommentComposer
 *
 * Comment writer slot at the foot of the comment list. Renders the editor only
 * for an authenticated user on a published planner; otherwise the matching
 * notice.
 */

import { useTranslation } from 'react-i18next'

import { CommentEditor } from './CommentEditor'

interface CommentComposerProps {
  isPublished: boolean
  isAuthenticated: boolean
  onSubmit: (content: string) => void
  isSubmitting: boolean
}

export function CommentComposer({
  isPublished,
  isAuthenticated,
  onSubmit,
  isSubmitting,
}: CommentComposerProps) {
  const { t } = useTranslation(['planner', 'common'])

  if (!isPublished) {
    return (
      <p className="text-muted-foreground text-sm">
        {t(
          'pages.plannerMD.comments.unpublished',
          'Comments are disabled while this planner is unpublished.',
        )}
      </p>
    )
  }

  if (!isAuthenticated) {
    return (
      <p className="text-muted-foreground text-sm">
        {t('pages.plannerMD.comments.loginRequired', 'Sign in to leave a comment.')}
      </p>
    )
  }

  return (
    <CommentEditor
      placeholder={t('pages.plannerMD.comments.placeholder', 'Write a comment...')}
      onSubmit={onSubmit}
      isSubmitting={isSubmitting}
    />
  )
}
