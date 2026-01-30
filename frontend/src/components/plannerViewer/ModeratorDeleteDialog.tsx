import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface ModeratorDeleteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  plannerTitle: string
  onConfirm: (reason: string) => void
  isPending: boolean
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
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    onConfirm(reason)
    setReason('') // Reset for next time
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('') // Reset on cancel
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('plannerTakedown.title')}</DialogTitle>
          <DialogDescription>
            {t('plannerTakedown.description', { title: plannerTitle })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <label htmlFor="takedown-reason" className="text-sm font-medium">
            {t('plannerTakedown.reason')}
          </label>
          <textarea
            id="takedown-reason"
            value={reason}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
            placeholder={t('plannerTakedown.reasonPlaceholder')}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
          />
          <p className="text-xs text-muted-foreground text-right">
            {reason.length}/500
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('plannerTakedown.cancel')}
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('plannerTakedown.takedown')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
