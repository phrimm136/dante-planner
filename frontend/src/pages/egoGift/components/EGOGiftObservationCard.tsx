import { EGOGiftTooltip } from './EGOGiftTooltip'
import type { EGOGiftId } from '@/shared/gameData'

interface EGOGiftObservationCardProps {
  giftId: EGOGiftId
  isSelected: boolean
  onSelect: (giftId: EGOGiftId) => void
  children: React.ReactNode
}

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
