import { memo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationCardProps {
  gift: EGOGiftListItem
  isSelected: boolean
  isSelectable: boolean
  onSelect: (giftId: string) => void
}

// Custom comparison - exclude onSelect callback which may change reference
// but maintains same behavior (functional update pattern in parent)
function areObservationCardPropsEqual(
  prev: EGOGiftObservationCardProps,
  next: EGOGiftObservationCardProps
): boolean {
  return (
    prev.gift.id === next.gift.id &&
    prev.isSelected === next.isSelected &&
    prev.isSelectable === next.isSelectable
    // onSelect excluded - callback identity may change but behavior is same
  )
}

/**
 * Gift card with tooltip (for observation/start gift selection)
 * Shows tooltip with description on hover
 * Memoized to prevent re-renders when props unchanged
 */
export const EGOGiftObservationCard = memo(function EGOGiftObservationCard({
  gift,
  isSelected,
  isSelectable,
  onSelect,
}: EGOGiftObservationCardProps) {
  return (
    <EGOGiftTooltip giftId={gift.id}>
      <button
        type="button"
        onClick={() => isSelectable && onSelect(gift.id)}
        disabled={!isSelectable}
        className="cursor-pointer"
      >
        <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
      </button>
    </EGOGiftTooltip>
  )
}, areObservationCardPropsEqual)
