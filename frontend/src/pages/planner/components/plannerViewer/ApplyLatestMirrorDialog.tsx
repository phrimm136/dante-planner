import { useTranslation } from 'react-i18next'

import {
  ConfirmActionDialog,
  type ActionDialogControl,
} from '@/components/feedback/ConfirmActionDialog'

interface ApplyLatestMirrorDialogProps extends ActionDialogControl {
  onConfirm: () => void
}

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
      {...(isPending !== undefined && { isPending })}
    />
  )
}
