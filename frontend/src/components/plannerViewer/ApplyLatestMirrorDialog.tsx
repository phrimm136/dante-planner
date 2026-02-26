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

interface ApplyLatestMirrorDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback when open state changes */
  onOpenChange: (open: boolean) => void
  /** Callback when user confirms */
  onConfirm: () => void
  /** Whether the apply operation is in progress */
  isPending: boolean
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('pages.plannerMD.applyLatestMirror.title')}</DialogTitle>
          <DialogDescription>
            {t('pages.plannerMD.applyLatestMirror.description')}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            {t('common:cancel')}
          </Button>
          <Button onClick={onConfirm} disabled={isPending}>
            {isPending
              ? t('pages.plannerMD.applyLatestMirror.applying')
              : t('pages.plannerMD.applyLatestMirror.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
