import { useState, useEffect } from 'react'

import { Slider } from '@/components/ui/slider'
import { cn } from '@/lib/utils'
import { getEGOTierIconPath, getEGOGiftEnhancementIconPath } from '@/lib/assetPaths'
import {
  MAX_LEVEL,
  type DetailEntityType,
  MAX_ENTITY_TIER,
  MIN_ENTITY_TIER,
  ENHANCEMENT_LABELS,
  type EnhancementLevel,
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
  disabledTiers = [],
}: DetailEntitySelectorProps) {
  const [inputValue, setInputValue] = useState(String(level))

  // Sync input value when level prop changes
  useEffect(() => {
    setInputValue(String(level))
  }, [level])

  const minTier = MIN_ENTITY_TIER[entityType]
  const maxTier = MAX_ENTITY_TIER[entityType]

  // Generate tier array based on entity type
  const tiers = Array.from({ length: maxTier - minTier + 1 }, (_, i) => minTier + i)

  const handleLevelInputChange = (value: string) => {
    setInputValue(value)
    const num = parseInt(value, 10)
    if (!isNaN(num) && num >= 1 && num <= MAX_LEVEL) {
      onLevelChange?.(num)
    }
  }

  const handleLevelInputBlur = () => {
    // Clamp and sync on blur
    const num = parseInt(inputValue, 10)
    if (isNaN(num) || num < 1) {
      setInputValue('1')
      onLevelChange?.(1)
    } else if (num > MAX_LEVEL) {
      setInputValue(String(MAX_LEVEL))
      onLevelChange?.(MAX_LEVEL)
    } else {
      setInputValue(String(num))
    }
  }

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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-6">
        {/* Tier selector */}
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-muted-foreground min-w-[70px]">
            {entityType === 'identity'
              ? 'Uptie'
              : entityType === 'ego'
                ? 'Threadspin'
                : 'Enhancement'}
          </span>
          <div className="flex gap-1">
            {tiers.map((t) => {
              const isSelected = tier === t
              const isDisabled = disabledTiers.includes(t)

              if (entityType === 'egoGift') {
                // Enhancement icons for EGO Gift
                const enhancementLevel = t as EnhancementLevel
                return (
                  <button
                    key={t}
                    type="button"
                    onClick={() => onTierChange(t)}
                    disabled={isDisabled}
                    className={cn(
                      'w-10 h-10 rounded flex items-center justify-center',
                      isDisabled
                        ? 'bg-muted/50 text-muted-foreground/50 cursor-not-allowed'
                        : 'selectable bg-card'
                    )}
                    data-selected={!isDisabled && isSelected}
                    aria-label={`Enhancement ${ENHANCEMENT_LABELS[enhancementLevel]}`}
                  >
                    {t === 0 ? (
                      <span className="text-sm font-medium">-</span>
                    ) : (
                      <img
                        src={getEGOGiftEnhancementIconPath(t)}
                        alt={`+${t}`}
                        className={cn('w-6 h-6 object-contain', isDisabled && 'opacity-50')}
                      />
                    )}
                  </button>
                )
              }

              // Tier icons for Identity/EGO
              return (
                <button
                  key={t}
                  type="button"
                  onClick={() => onTierChange(t)}
                  className={cn(
                    'selectable w-10 h-10 rounded flex items-center justify-center',
                    isSelected ? 'brightness-125' : 'opacity-60 hover:opacity-100'
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
          <div className="flex items-center gap-3 flex-1">
            <span className="text-sm font-medium text-muted-foreground">Level</span>
            <div className="flex-1 max-w-[200px]">
              <Slider
                value={[level]}
                onValueChange={handleSliderChange}
                min={1}
                max={MAX_LEVEL}
                step={1}
              />
            </div>
            <input
              type="number"
              value={inputValue}
              onChange={(e) => handleLevelInputChange(e.target.value)}
              onBlur={handleLevelInputBlur}
              min={1}
              max={MAX_LEVEL}
              className="w-14 h-8 px-2 text-center text-sm bg-muted border rounded"
            />
          </div>
        )}
      </div>
    </div>
  )
}

export default DetailEntitySelector
