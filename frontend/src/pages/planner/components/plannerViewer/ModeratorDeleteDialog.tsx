import { useTranslation } from 'react-i18next'

import { ModerationReasonDialog } from '@/shared/moderation'
import type { ActionDialogControl } from '@/components/feedback/ConfirmActionDialog'

interface ModeratorDeleteDialogProps extends ActionDialogControl {
  plannerTitle: string
  onConfirm: (reason: string) => void
}

/**
 * Dialog for moderator planner takedown with reason input.
 * Takedown removes planner from public but allows owner to keep syncing.
 */
export function ModeratorDeleteDialog({
  open,
  onOpenChange,
  plannerTitle,
  onConfirm,
  isPending,
}: ModeratorDeleteDialogProps) {
  const { t } = useTranslation(['moderation'])

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('plannerTakedown.title')}
      description={t('plannerTakedown.description', { title: plannerTitle })}
      reasonLabel={t('plannerTakedown.reason')}
      reasonPlaceholder={t('plannerTakedown.reasonPlaceholder')}
      reasonInputId="takedown-reason"
      cancelLabel={t('plannerTakedown.cancel')}
      confirmLabel={t('plannerTakedown.takedown')}
      destructive
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}
