import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getAffinityIconPath } from '@/shared/assets'
import { AFFINITIES, type Affinity } from '@/shared/gameData'

import { ResistReading } from './ResistancePanel'
import type { Shown } from './StatusPanel'

interface SinResistancePanelProps {
  resistances: Shown<Record<string, number>>
}

export function SinResistancePanel({ resistances }: SinResistancePanelProps) {
  const { t } = useTranslation('database')

  return (
    <LabeledPanel title={t('identity.resist.sinResistance')}>
      <div className="grid grid-cols-7 gap-1">
        {AFFINITIES.map((affinity: Affinity) => (
          <div key={affinity} className="flex flex-col items-center gap-1">
            <img
              src={getAffinityIconPath(affinity)}
              alt={affinity}
              className="w-6 h-6 object-contain"
            />
            <ResistReading
              value={
                resistances.kind === 'shown' ? (resistances.value[affinity] ?? 1.0) : undefined
              }
            />
          </div>
        ))}
      </div>
    </LabeledPanel>
  )
}
