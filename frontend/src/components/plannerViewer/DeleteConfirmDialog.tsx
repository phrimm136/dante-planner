import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface DeleteConfirmDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback when open state changes */
  onOpenChange: (open: boolean) => void
  /** ID of the planner to delete */
  plannerId: string
  /** Title of the planner to delete (for display) */
  plannerTitle: string
  /** Callback when user confirms deletion */
  onConfirm: () => void
  /** Whether deletion is in progress */
  isPending: boolean
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('pages.detail.deleteConfirm.title')}</DialogTitle>
          <DialogDescription>
            {t('pages.detail.deleteConfirm.description', { title: plannerTitle })}
          </DialogDescription>
        </DialogHeader>
        <p className="text-sm text-destructive">
          {t('pages.detail.deleteConfirm.warning')}
        </p>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            {t('common:cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={isPending}
          >
            {isPending
              ? t('pages.detail.deleteConfirm.deleting')
              : t('pages.detail.deleteConfirm.delete')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
