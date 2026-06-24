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

interface VoteWarningModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  voteDirection: 'UP' | 'DOWN'
  plannerId: string
}

/**
 * VoteWarningModal - Pre-vote confirmation dialog
 *
 * Warns users that votes are PERMANENT before they cast their first vote on a planner.
 * Shows once per planner (tracked via localStorage: `vote-warning-shown-${plannerId}`).
 *
 * @param open - Whether dialog is open
 * @param onOpenChange - Callback when open state changes
 * @param onConfirm - Callback when user clicks "I Understand"
 * @param voteDirection - Vote direction ('UP' or 'DOWN') to show appropriate message
 * @param plannerId - Planner ID for localStorage tracking
 */
export function VoteWarningModal({
  open,
  onOpenChange,
  onConfirm,
  voteDirection,
  plannerId,
}: VoteWarningModalProps) {
  const { t } = useTranslation('planner')

  const handleConfirm = () => {
    // Mark warning as shown for this planner
    // Fallback: If localStorage fails (disabled/full/unavailable), still proceed with vote
    try {
      localStorage.setItem(`vote-warning-shown-${plannerId}`, 'true')
    } catch (error) {
      // localStorage unavailable (private browsing, quota exceeded, disabled)
      // Log error but don't block vote - warning is courtesy, not requirement
      console.warn('Failed to save vote warning state to localStorage:', error)
    }
    onConfirm()
    onOpenChange(false)
  }

  const handleCancel = () => {
    onOpenChange(false)
  }

  const messageKey =
    voteDirection === 'UP' ? 'voteWarning.messageUp' : 'voteWarning.messageDown'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-[400px] sm:max-w-[90vw]">
        <DialogHeader>
          <DialogTitle>{t('voteWarning.title')}</DialogTitle>
          <DialogDescription className="text-base font-medium pt-2">
            {t(messageKey)}
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            {t('voteWarning.cancel')}
          </Button>
          <Button variant="destructive" onClick={handleConfirm}>
            {t('voteWarning.understand')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
