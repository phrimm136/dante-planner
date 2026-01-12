import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { Slider } from '@/components/ui/slider'
import { cn, getDisplayFontForLabel, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'
import { getEGOTierIconPath } from '@/lib/assetPaths'
import {
  MAX_LEVEL,
  type DetailEntityType,
  MAX_ENTITY_TIER,
  MIN_ENTITY_TIER,
} from '@/lib/constants'

interface DetailEntitySelectorProps {
  /** Entity type determines icon style and tier range */
  entityType: DetailEntityType
  /** Current tier/uptie/threadspin/enhancement level */
  tier: number
  /** Callback when tier changes */
  onTierChange: (tier: number) => void
  /** Current level (only used for identity) */
  level?: number
  /** Callback when level changes (only used for identity) */
  onLevelChange?: (level: number) => void
  /** Whether the selector should be sticky */
  sticky?: boolean
  /** Tiers to disable (e.g., enhancement levels with empty descriptions) */
  disabledTiers?: number[]
}

/**
 * DetailEntitySelector - Unified tier/level selector for detail pages
 *
 * Supports three entity types:
 * - Identity: Uptie 1-4 (tier icons) + Level 1-55 (slider)
 * - EGO: Threadspin 1-4 (tier icons), no level
 * - EGO Gift: Enhancement 0-2 (enhancement icons), no level
 *
 * Pattern: TierLevelSelector.tsx (tier icons), EGOGiftEnhancementSelector.tsx (enhancement icons)
 */
export function DetailEntitySelector({
  entityType,
  tier,
  onTierChange,
  level = MAX_LEVEL,
  onLevelChange,
  sticky = false,
}: DetailEntitySelectorProps) {
  const { t, i18n } = useTranslation('database')
  const [inputValue, setInputValue] = useState(String(level))
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  // Sync input value when level prop changes
  useEffect(() => {
    setInputValue(String(level))
  }, [level])

  const minTier = MIN_ENTITY_TIER[entityType]
  const maxTier = MAX_ENTITY_TIER[entityType]

  // Generate tier array based on entity type
  const tiers = Array.from({ length: maxTier - minTier + 1 }, (_, i) => minTier + i)

  const handleSliderChange = (values: number[]) => {
    const newLevel = values[0]
    setInputValue(String(newLevel))
    onLevelChange?.(newLevel)
  }

  // Only Identity has level slider
  const showLevelSelector = entityType === 'identity'

  return (
    <div
      className={cn(
        'bg-background/95 backdrop-blur-sm border rounded-lg p-4',
        sticky && 'sticky top-0 z-10'
      )}
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:gap-6">
        {/* Tier selector */}
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-muted-foreground" style={displayStyle}>
            {entityType === 'identity'
              ? t('tierLabel.uptie')
              : entityType === 'ego'
                ? t('tierLabel.threadspin')
                : t('tierLabel.enhancement')}
          </span>
          <div className="flex gap-1">
            {tiers.map((t) => {
              const isSelected = tier === t

              // Tier icons for Identity/EGO
              return (
                <button
                  key={t}
                  type="button"
                  onClick={() => onTierChange(t)}
                  className={cn(
                    'selectable w-10 h-10 rounded flex items-center justify-center translation-250ms',
                    !isSelected && 'opacity-60 hover:opacity-100'
                  )}
                  data-selected={isSelected}
                  aria-label={`Tier ${t}`}
                >
                  <img
                    src={getEGOTierIconPath(t)}
                    alt={`Tier ${t}`}
                    className="w-8 h-8 object-contain"
                  />
                </button>
              )
            })}
          </div>
        </div>

        {/* Level selector (only for identity) */}
        {showLevelSelector && (
          <div className="flex items-center gap-1 flex-1">
            <span
              className="text-lg font-medium text-muted-foreground translate-y-1"
              style={{ fontFamily: getDisplayFontForLabel() }}
            >
              LV
            </span>
            <div
              className="text-center text-[28px] -translate-y-1 mr-2"
              style={{ fontFamily: getDisplayFontForNumeric() }}
            >
              {inputValue}
            </div>
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

export default DetailEntitySelector
