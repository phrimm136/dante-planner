import { useTranslation } from 'react-i18next'

export function DeletedCommentPlaceholder() {
  const { t } = useTranslation(['planner'])

  return (
    <div className="py-3">
      <div className="flex items-center gap-2 text-sm mb-1">
        <span className="font-medium text-muted-foreground">
          [{t('pages.plannerMD.comments.deletedUser')}]
        </span>
      </div>

      <div className="text-sm text-muted-foreground italic">
        {t('pages.plannerMD.comments.deletedComment')}
      </div>
    </div>
  )
}
