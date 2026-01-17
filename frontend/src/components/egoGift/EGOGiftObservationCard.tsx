import { memo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationCardProps {
  gift: EGOGiftListItem
  isSelected: boolean
  onSelect: (giftId: string) => void
}

// Custom comparison - exclude onSelect callback which may change reference
function areObservationCardPropsEqual(
  prev: EGOGiftObservationCardProps,
  next: EGOGiftObservationCardProps
): boolean {
  return (
    prev.gift.id === next.gift.id &&
    prev.isSelected === next.isSelected
  )
}

/**
 * Gift card with tooltip (for observation/start gift selection)
 * Visibility handled by parent wrapper div for optimal performance
 */
export const EGOGiftObservationCard = memo(function EGOGiftObservationCard({
  gift,
  isSelected,
  onSelect,
}: EGOGiftObservationCardProps) {
  return (
    <EGOGiftTooltip giftId={gift.id}>
      <button
        type="button"
        onClick={() => { onSelect(gift.id); }}
        className="cursor-pointer"
      >
        <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
      </button>
    </EGOGiftTooltip>
  )
}, areObservationCardPropsEqual)
