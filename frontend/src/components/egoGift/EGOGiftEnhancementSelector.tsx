import {
  ENHANCEMENT_LEVELS,
  ENHANCEMENT_LABELS,
  type EnhancementLevel,
} from '@/lib/constants'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { EGOGiftTooltipContent } from './EGOGiftTooltipContent'

interface EGOGiftEnhancementSelectorProps {
  giftId: string
  currentEnhancement: EnhancementLevel
  isSelected: boolean
  onSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * Enhancement level selector overlay for EGO gift cards
 * Shows 3 enhancement options (-, +, ++) in a horizontal row
 * Follows TierLevelSelector hover pattern with absolute positioning
 */
export function EGOGiftEnhancementSelector({
  giftId,
  currentEnhancement,
  isSelected,
  onSelect,
}: EGOGiftEnhancementSelectorProps) {
  const handleLevelClick = (e: React.MouseEvent, level: EnhancementLevel) => {
    e.preventDefault()
    e.stopPropagation()
    onSelect(giftId, level)
  }

  return (
    <div className="absolute z-50 top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-auto">
      <div className="flex bg-black/80 rounded px-1 py-0.5 gap-1">
        {ENHANCEMENT_LEVELS.map((level) => {
          const isCurrentLevel = isSelected && currentEnhancement === level

          return (
            <Tooltip key={level}>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={(e) => handleLevelClick(e, level)}
                  className={`w-7 h-7 rounded text-xs font-bold transition-all flex items-center justify-center ${
                    isCurrentLevel
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
                  }`}
                >
                  {ENHANCEMENT_LABELS[level]}
                </button>
              </TooltipTrigger>
              <TooltipContent
                side="bottom"
                className="max-w-xs bg-gray-900 border border-gray-700 p-3"
              >
                <EGOGiftTooltipContent giftId={giftId} enhancement={level} />
              </TooltipContent>
            </Tooltip>
          )
        })}
      </div>
    </div>
  )
}
