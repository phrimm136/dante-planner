import { useTranslation } from 'react-i18next'

import {
  ConfirmActionDialog,
  type ActionDialogControl,
} from '@/components/feedback/ConfirmActionDialog'

interface DeleteConfirmDialogProps extends ActionDialogControl {
  plannerId: string
  plannerTitle: string
  onConfirm: () => void
}

export function DeleteConfirmDialog({
  open,
  onOpenChange,
  plannerTitle,
  onConfirm,
  isPending,
}: DeleteConfirmDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.detail.deleteConfirm.title')}
      description={t('pages.detail.deleteConfirm.description', { title: plannerTitle })}
      cancelLabel={t('common:cancel')}
      confirmLabel={t('pages.detail.deleteConfirm.delete')}
      pendingLabel={t('pages.detail.deleteConfirm.deleting')}
      destructive
      onConfirm={onConfirm}
      {...(isPending !== undefined && { isPending })}
    >
      <p className="text-sm text-destructive">{t('pages.detail.deleteConfirm.warning')}</p>
    </ConfirmActionDialog>
  )
}
