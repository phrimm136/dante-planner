import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Slider } from '@/components/ui/slider'
import { cn, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'
import { MIN_LEVEL } from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'

interface TierSelectorProps {
  tierLabel: ReactNode
  minTier: number
  maxTier: number
  tier: number
  onTierChange: (tier: number) => void
  tierIconPath: (tier: number) => string
  sticky?: boolean
}

interface LevelControlProps {
  level: number
  maxLevel: number
  onLevelChange: (level: number) => void
}

type DetailEntitySelectorProps = TierSelectorProps &
  (LevelControlProps | { [K in keyof LevelControlProps]?: never })

export function DetailEntitySelector({
  tierLabel,
  minTier,
  maxTier,
  tier,
  onTierChange,
  tierIconPath,
  level,
  maxLevel,
  onLevelChange,
  sticky = false,
}: DetailEntitySelectorProps) {
  const { t, i18n } = useTranslation('database')
  const [inputValue, setInputValue] = useState(String(level))
  const [appliedLevel, setAppliedLevel] = useState(level)
  const displayStyle = getDisplayFontForLanguage(i18n.language)

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

        {maxLevel !== undefined && (
          <div
            className="flex items-center gap-3 flex-1"
            style={{ fontFamily: getDisplayFontForNumeric() }}
          >
            <span className="text-[26px] -translate-y-1">{`Lv. ${inputValue}`}</span>
            <div className="flex-1 max-w-[200px]">
              <Slider
                value={[level]}
                onValueChange={handleSliderChange}
                min={MIN_LEVEL}
                max={maxLevel}
                step={1}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
