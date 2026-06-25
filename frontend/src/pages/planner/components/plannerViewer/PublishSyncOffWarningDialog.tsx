import { useTranslation } from 'react-i18next'
import { AlertTriangle } from 'lucide-react'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface PublishSyncOffWarningDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  isPending?: boolean
}

/**
 * Warning dialog shown when user tries to publish a plan with sync disabled.
 * Warns that publishing will upload the plan to server and make it public.
 *
 * @example
 * ```tsx
 * <PublishSyncOffWarningDialog
 *   open={showPublishWarning}
 *   onOpenChange={setShowPublishWarning}
 *   onConfirm={handlePublishWithUpload}
 *   isPending={isUploading}
 * />
 * ```
 */
export function PublishSyncOffWarningDialog({
  open,
  onOpenChange,
  onConfirm,
  isPending = false,
}: PublishSyncOffWarningDialogProps) {
  const { t } = useTranslation('planner')

  const handleConfirm = () => {
    onConfirm()
  }

  const handleCancel = () => {
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <AlertTriangle className="size-5 text-yellow-500" />
            <DialogTitle>{t('pages.plannerMD.publish.syncOffWarning.title')}</DialogTitle>
          </div>
          <DialogDescription className="pt-2">
            {t('pages.plannerMD.publish.syncOffWarning.description')}
          </DialogDescription>
        </DialogHeader>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={handleCancel}
            disabled={isPending}
          >
            {t('pages.plannerMD.publish.syncOffWarning.cancel')}
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={isPending}
          >
            {t('pages.plannerMD.publish.syncOffWarning.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
