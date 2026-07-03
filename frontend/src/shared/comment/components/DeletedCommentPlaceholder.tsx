/**
 * DeletedCommentPlaceholder
 *
 * Placeholder shown for deleted comments to preserve thread structure.
 * Maintains same layout as CommentCard:
 * - Row 1: [deleted] author label (no date, no actions)
 * - Row 2: "Deleted" content message
 */

import { useTranslation } from 'react-i18next'

export function DeletedCommentPlaceholder() {
  const { t } = useTranslation(['planner'])

  return (
    <div className="py-3">
      {/* Row 1: Author label (mirrors CommentCard layout) */}
      <div className="flex items-center gap-2 text-sm mb-1">
        <span className="font-medium text-muted-foreground">
          [{t('pages.plannerMD.comments.deletedUser')}]
        </span>
      </div>

      {/* Row 2: Deleted content message */}
      <div className="text-sm text-muted-foreground italic">
        {t('pages.plannerMD.comments.deletedComment')}
      </div>
    </div>
  )
}
