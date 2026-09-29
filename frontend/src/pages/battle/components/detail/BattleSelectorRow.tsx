import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { getDisplayFontForLanguage } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'

export interface SelectorOption<T> {
  value: T
  label: ReactNode
}

interface SelectorGroupProps<T> {
  label: string
  options: readonly SelectorOption<T>[]
  selected: T
  onSelect: (value: T) => void
}

function SelectorGroup<T extends string | number>({
  label,
  options,
  selected,
  onSelect,
}: SelectorGroupProps<T>) {
  const { i18n } = useTranslation()

  return (
    <div role="group" aria-label={label} className={SECTION_STYLES.LAYOUT.row}>
      <span className="text-lg font-medium" style={getDisplayFontForLanguage(i18n.language)}>
        {label}
      </span>
      <div className="flex flex-wrap gap-1">
        {options.map((option) => {
          const isSelected = option.value === selected
          return (
            <button
              key={String(option.value)}
              type="button"
              aria-pressed={isSelected}
              data-selected={isSelected}
              onClick={() => {
                onSelect(option.value)
              }}
              className="selectable h-10 rounded-md border border-border px-3 text-sm"
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

interface BattleSelectorRowProps<TPart extends string | number> {
  phaseCount: number
  phaseIndex: number
  onPhaseChange: (index: number) => void
  partOptions: readonly SelectorOption<TPart>[]
  part: TPart
  onPartChange: (part: TPart) => void
  levels: readonly number[]
  level: number | undefined
  onLevelChange: (level: number) => void
}

export function BattleSelectorRow<TPart extends string | number>({
  phaseCount,
  phaseIndex,
  onPhaseChange,
  partOptions,
  part,
  onPartChange,
  levels,
  level,
  onLevelChange,
}: BattleSelectorRowProps<TPart>) {
  const { t } = useTranslation('database')

  const phaseOptions = Array.from({ length: phaseCount }, (_, index) => ({
    value: index,
    label: t('battle.detail.phaseOption', {
      n: String(index + 1),
    }),
  }))
  const levelOptions = levels.map((value) => ({
    value,
    label: t('battle.detail.levelOption', { level: String(value) }),
  }))

  return (
    <div className="bg-background/95 backdrop-blur-sm border rounded-lg p-4 sticky top-0 z-10">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:gap-6">
        {phaseCount > 1 && (
          <SelectorGroup
            label={t('battle.detail.phase')}
            options={phaseOptions}
            selected={phaseIndex}
            onSelect={onPhaseChange}
          />
        )}
        {partOptions.length > 0 && (
          <SelectorGroup
            label={t('battle.detail.part')}
            options={partOptions}
            selected={part}
            onSelect={onPartChange}
          />
        )}
        {level !== undefined && (
          <SelectorGroup
            label={t('battle.detail.level')}
            options={levelOptions}
            selected={level}
            onSelect={onLevelChange}
          />
        )}
      </div>
    </div>
  )
}
