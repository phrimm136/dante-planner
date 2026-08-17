import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationCardProps {
  giftId: string
  isSelected: boolean
  onSelect: (giftId: string) => void
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
        className="cursor-pointer"
      >
        {children}
      </button>
    </EGOGiftTooltip>
  )
}
