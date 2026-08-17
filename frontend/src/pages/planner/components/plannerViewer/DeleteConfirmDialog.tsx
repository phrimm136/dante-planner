import { useTranslation } from 'react-i18next'

import {
  ConfirmActionDialog,
  type ActionDialogControl,
} from '@/components/feedback/ConfirmActionDialog'

interface DeleteConfirmDialogProps extends ActionDialogControl {
  /** ID of the planner to delete */
  plannerId: string
  /** Title of the planner to delete (for display) */
  plannerTitle: string
  /** Callback when user confirms deletion */
  onConfirm: () => void
}

/**
 * Confirmation dialog for planner deletion.
 * Warns user that deletion is permanent.
 *
 * @example
 * <DeleteConfirmDialog
 *   open={showDeleteDialog}
 *   onOpenChange={setShowDeleteDialog}
 *   plannerId={planner.id}
 *   plannerTitle={planner.title}
 *   onConfirm={handleDelete}
 *   isPending={deleteMutation.isPending}
 * />
 */
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
