import { useTranslation } from 'react-i18next'

import { ConfirmActionDialog } from '@/components/feedback/ConfirmActionDialog'

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
    // Warning is a courtesy: an unavailable localStorage must not block the vote
    try {
      localStorage.setItem(`vote-warning-shown-${plannerId}`, 'true')
    } catch (error) {
      console.warn('Failed to save vote warning state to localStorage:', error)
    }
    onConfirm()
    onOpenChange(false)
  }

  const messageKey = voteDirection === 'UP' ? 'voteWarning.messageUp' : 'voteWarning.messageDown'

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      className="w-full max-w-[400px] sm:max-w-[90vw]"
      title={t('voteWarning.title')}
      description={t(messageKey)}
      descriptionClassName="text-base font-medium pt-2"
      cancelLabel={t('voteWarning.cancel')}
      confirmLabel={t('voteWarning.understand')}
      destructive
      onConfirm={handleConfirm}
    />
  )
}
