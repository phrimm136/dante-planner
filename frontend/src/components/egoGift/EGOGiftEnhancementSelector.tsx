import {
  ENHANCEMENT_LEVELS,
  ENHANCEMENT_LABELS,
  type EnhancementLevel,
} from '@/lib/constants'
import { EGOGiftTooltip } from './EGOGiftTooltip'

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
            <EGOGiftTooltip key={level} giftId={giftId} enhancement={level}>
              <button
                type="button"
                onClick={(e) => { handleLevelClick(e, level); }}
                className="selectable w-7 h-7 rounded text-xs font-bold flex items-center justify-center bg-card/80"
                data-selected={isCurrentLevel}
              >
                {ENHANCEMENT_LABELS[level]}
              </button>
            </EGOGiftTooltip>
          )
        })}
      </div>
    </div>
  )
}
