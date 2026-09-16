import { EGOGiftTooltip } from './EGOGiftTooltip'
import type { EGOGiftId } from '@/shared/gameData'

interface EGOGiftObservationCardProps {
  giftId: EGOGiftId
  isSelected: boolean
  onSelect: (giftId: EGOGiftId) => void
  children: React.ReactNode
}

/**
 * Gift card wrapper with tooltip (for observation/start gift selection)
 * Uses children pattern for consistent DevTools display with SelectableCard
 */
export const EGOGiftObservationCard = function EGOGiftObservationCard({
  giftId,
  isSelected: _isSelected,
  onSelect,
  children,
}: EGOGiftObservationCardProps) {
  return (
    <EGOGiftTooltip giftId={giftId}>
      <button
        type="button"
        onClick={() => {
          onSelect(giftId)
        }}
        className="block w-full cursor-pointer"
      >
        {children}
      </button>
    </EGOGiftTooltip>
  )
}
