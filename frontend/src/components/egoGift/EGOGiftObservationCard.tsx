import { memo } from 'react'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationCardProps {
  giftId: string
  onSelect: (giftId: string) => void
  children: React.ReactNode
}

// Custom comparison - exclude children and callback
function areObservationCardPropsEqual(
  prev: EGOGiftObservationCardProps,
  next: EGOGiftObservationCardProps
): boolean {
  return prev.giftId === next.giftId
  // children excluded - inner card handles its own memoization
  // onSelect excluded - callback identity may change but behavior is same
}

/**
 * Gift card wrapper with tooltip (for observation/start gift selection)
 * Uses children pattern for consistent DevTools display with SelectableCard
 */
export const EGOGiftObservationCard = memo(function EGOGiftObservationCard({
  giftId,
  onSelect,
  children,
}: EGOGiftObservationCardProps) {
  return (
    <EGOGiftTooltip giftId={giftId}>
      <button
        type="button"
        onClick={() => { onSelect(giftId); }}
        className="cursor-pointer"
      >
        {children}
      </button>
    </EGOGiftTooltip>
  )
}, areObservationCardPropsEqual)
