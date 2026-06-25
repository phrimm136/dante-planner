import {
  ENHANCEMENT_LEVELS,
  ENHANCEMENT_LABELS,
  type EnhancementLevel,
} from '@/lib/constants'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftEnhancementSelectorProps {
  giftId: string
  currentEnhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * Enhancement level selector overlay for EGO gift cards
 * Shows enhancement options (-, +, ++) up to maxEnhancement
 * Follows TierLevelSelector hover pattern with absolute positioning
 */
export function EGOGiftEnhancementSelector({
  giftId,
  currentEnhancement,
  maxEnhancement,
  isSelected,
  onSelect,
}: EGOGiftEnhancementSelectorProps) {
  const handleLevelClick = (e: React.MouseEvent, level: EnhancementLevel) => {
    e.preventDefault()
    e.stopPropagation()
    onSelect(giftId, level)
  }

  // Filter to only show levels up to maxEnhancement
  const availableLevels = ENHANCEMENT_LEVELS.filter((level) => level <= maxEnhancement)

  return (
    <div className="absolute z-50 top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-auto">
      <div className="flex bg-black/80 rounded px-1 py-0.5 gap-1">
        {availableLevels.map((level) => {
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
