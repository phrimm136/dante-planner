import { memo } from 'react'
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
 * Memoized to prevent re-renders when props unchanged
 */
export const EGOGiftObservationCard = memo(function EGOGiftObservationCard({
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
          className="cursor-pointer"
        >
          <EGOGiftCard gift={gift} isSelected={isSelected} />
        </button>
      </TooltipTrigger>
      <TooltipContent
        side="bottom"
        className="w-auto max-w-md bg-black/85 border-neutral-800 text-foreground rounded-none p-2"
      >
        <EGOGiftTooltipContent giftId={gift.id} enhancement={0} />
      </TooltipContent>
    </Tooltip>
  )
})
