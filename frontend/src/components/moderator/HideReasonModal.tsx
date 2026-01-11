import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { useHideFromRecommendedMutation } from '@/hooks/useHideFromRecommendedMutation'

interface HideReasonModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  plannerId: string
  plannerTitle: string
}

/**
 * HideReasonModal - Moderator/Admin dialog for hiding planner from recommended
 *
 * Prompts moderator to enter reason for hiding planner (min 10 chars).
 * Uses useHideFromRecommendedMutation hook to execute hide action.
 *
 * @param open - Whether dialog is open
 * @param onOpenChange - Callback when open state changes
 * @param plannerId - UUID of planner to hide
 * @param plannerTitle - Title of planner (shown for context)
 */
export function HideReasonModal({
  open,
  onOpenChange,
  plannerId,
  plannerTitle,
}: HideReasonModalProps) {
  const { t } = useTranslation('planner')
  const [reason, setReason] = useState('')
  const hideFromRecommended = useHideFromRecommendedMutation()

  const handleConfirm = () => {
    if (reason.trim().length < 10) return

    hideFromRecommended.mutate(
      { plannerId, reason: reason.trim() },
      {
        onSuccess: () => {
          setReason('')
          onOpenChange(false)
        },
      }
    )
  }

  const handleCancel = () => {
    setReason('')
    onOpenChange(false)
  }

  const isValid = reason.trim().length >= 10

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-[500px] sm:max-w-[90vw]">
        <DialogHeader>
          <DialogTitle>{t('moderation.hideButton')}</DialogTitle>
          <DialogDescription className="pt-2">
            {plannerTitle}
          </DialogDescription>
        </DialogHeader>

        <div className="py-4">
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('moderation.hideReasonPlaceholder')}
            className="min-h-[120px]"
            maxLength={500}
          />
          <p className="text-sm text-muted-foreground mt-2">
            {reason.length}/500 {!isValid && '(minimum 10 characters)'}
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            {t('voteWarning.cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={!isValid || hideFromRecommended.isPending}
          >
            {hideFromRecommended.isPending
              ? t('common.loading')
              : t('moderation.hideConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
