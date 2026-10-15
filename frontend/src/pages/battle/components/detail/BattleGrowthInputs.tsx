import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import { phaseTexts } from '../../lib/battleDetail'
import { clampGrowthInput, type GrowthInputs } from '../../lib/battleGrowth'
import type { BattleGrowth } from '../../schemas/BattleDetailSchemas'
import type { BattleTextSource } from './BattleSkillCard'

interface BattleGrowthInputsProps {
  source: BattleTextSource
  growth: BattleGrowth
  inputs: GrowthInputs
  onChange: (track: string, value: number) => void
}

function TrackInputs({ source, growth, inputs, onChange }: BattleGrowthInputsProps) {
  const i18n = useBattleDetailI18n(source.config, source.id)
  const names = phaseTexts(i18n, source.unitId, source.phaseId)?.conditionNames

  return growth.tracks.map((track) => (
    <label key={track} className="flex items-center gap-2 text-sm">
      <span>{names?.[track] ?? track}</span>
      <Input
        type="number"
        min={0}
        max={growth.max}
        step={1}
        value={inputs[track] ?? growth.max}
        onChange={(event) => {
          onChange(track, clampGrowthInput(event.currentTarget.valueAsNumber, growth))
        }}
        className="h-10 w-24"
      />
    </label>
  ))
}

export function BattleGrowthInputs(props: BattleGrowthInputsProps) {
  const { t, i18n } = useTranslation('database')
  const label = t('battle.detail.growth')

  return (
    <div role="group" aria-label={label} className={SECTION_STYLES.LAYOUT.row}>
      <span
        className="shrink-0 text-lg font-medium"
        style={getDisplayFontForLanguage(i18n.language)}
      >
        {label}
      </span>
      <div className="flex flex-wrap gap-3">
        <Suspense fallback={<Skeleton className="h-10 w-64" />}>
          <TrackInputs {...props} />
        </Suspense>
      </div>
    </div>
  )
}
