import { useTranslation } from 'react-i18next'

import {
  ConfirmActionDialog,
  type ActionDialogControl,
} from '@/components/feedback/ConfirmActionDialog'

interface ApplyLatestMirrorDialogProps extends ActionDialogControl {
  /** Callback when user confirms */
  onConfirm: () => void
}

/**
 * Confirmation dialog for applying the latest Mirror Dungeon content version.
 * Warns user that the action is permanent.
 *
 * @example
 * <ApplyLatestMirrorDialog
 *   open={showDialog}
 *   onOpenChange={setShowDialog}
 *   onConfirm={handleApply}
 *   isPending={isApplying}
 * />
 */
export function ApplyLatestMirrorDialog({
  open,
  onOpenChange,
  onConfirm,
  isPending,
}: ApplyLatestMirrorDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.plannerMD.applyLatestMirror.title')}
      description={t('pages.plannerMD.applyLatestMirror.description')}
      cancelLabel={t('common:cancel')}
      confirmLabel={t('pages.plannerMD.applyLatestMirror.confirm')}
      pendingLabel={t('pages.plannerMD.applyLatestMirror.applying')}
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}
