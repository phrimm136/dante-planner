import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltipContent } from './EGOGiftTooltipContent'

interface EGOGiftObservationCardProps {
  gift: EGOGiftListItem
  isSelected: boolean
  isSelectable: boolean
  onSelect: (giftId: string) => void
}

/**
 * Gift card with tooltip (for observation/start gift selection)
 * Shows tooltip with description on hover
 */
export function EGOGiftObservationCard({
  gift,
  isSelected,
  isSelectable,
  onSelect,
}: EGOGiftObservationCardProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          onClick={() => isSelectable && onSelect(gift.id)}
          disabled={!isSelectable}
          className={!isSelectable ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
        >
          <EGOGiftCard gift={gift} isSelected={isSelected} />
        </button>
      </TooltipTrigger>
      <TooltipContent
        side="bottom"
        className="max-w-xs bg-gray-900 border border-gray-700 p-3"
      >
        <EGOGiftTooltipContent giftId={gift.id} enhancement={0} />
      </TooltipContent>
    </Tooltip>
  )
}
