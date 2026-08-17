import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Slider } from '@/components/ui/slider'
import { cn, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'
import { MAX_LEVEL } from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'

interface DetailEntitySelectorProps {
  /** Heading for the tier row, already resolved by the caller */
  tierLabel: ReactNode
  /** Lowest selectable tier */
  minTier: number
  /** Highest selectable tier */
  maxTier: number
  /** Current tier/uptie/threadspin/enhancement level */
  tier: number
  /** Callback when tier changes */
  onTierChange: (tier: number) => void
  /** Icon shown on a tier button */
  tierIconPath: (tier: number) => string
  /** Current level; the level slider renders only when `onLevelChange` is given */
  level?: number
  /** Callback when level changes */
  onLevelChange?: (level: number) => void
  /** Whether the selector should be sticky */
  sticky?: boolean
}

/**
 * DetailEntitySelector - Tier selector for detail pages, with an optional level slider.
 *
 * Pattern: TierLevelSelector.tsx (tier icons), EGOGiftEnhancementSelector.tsx (enhancement icons)
 */
export function DetailEntitySelector({
  tierLabel,
  minTier,
  maxTier,
  tier,
  onTierChange,
  tierIconPath,
  level = MAX_LEVEL,
  onLevelChange,
  sticky = false,
}: DetailEntitySelectorProps) {
  const { t, i18n } = useTranslation('database')
  const [inputValue, setInputValue] = useState(String(level))
  const [appliedLevel, setAppliedLevel] = useState(level)
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  // A level set from outside replaces whatever is being typed.
  if (level !== appliedLevel) {
    setAppliedLevel(level)
    setInputValue(String(level))
  }

  const tiers = Array.from({ length: maxTier - minTier + 1 }, (_, i) => minTier + i)

  const handleSliderChange = ([newLevel]: number[]) => {
    if (newLevel === undefined) return
    setInputValue(String(newLevel))
    onLevelChange?.(newLevel)
  }

  return (
    <div
      className={cn(
        'bg-background/95 backdrop-blur-sm border rounded-lg p-4',
        sticky && 'sticky top-0 z-10',
      )}
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:gap-6">
        {/* Tier selector */}
        <div className={SECTION_STYLES.LAYOUT.row}>
          <span className="text-lg font-medium" style={displayStyle}>
            {tierLabel}
          </span>
          <div className="flex gap-1">
            {tiers.map((tierOption) => {
              const isSelected = tier === tierOption
              const tierName = `${t('filters.tier')} ${tierOption}`

              return (
                <button
                  key={tierOption}
                  type="button"
                  onClick={() => onTierChange(tierOption)}
                  className={cn(
                    'selectable w-10 h-10 rounded flex items-center justify-center translation-250ms',
                    !isSelected && 'opacity-60 hover:opacity-100',
                  )}
                  data-selected={isSelected}
                  aria-label={tierName}
                >
                  <img src={tierIconPath(tierOption)} alt="" className="w-8 h-8 object-contain" />
                </button>
              )
            })}
          </div>
        </div>

        {/* Level selector */}
        {onLevelChange && (
          <div
            className="flex items-center gap-3 flex-1"
            style={{ fontFamily: getDisplayFontForNumeric() }}
          >
            <span className="text-[26px] -translate-y-1">{`Lv. ${inputValue}`}</span>
            <div className="flex-1 max-w-[200px]">
              <Slider
                value={[level]}
                onValueChange={handleSliderChange}
                min={1}
                max={MAX_LEVEL}
                step={1}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
