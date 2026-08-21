import { useTranslation } from 'react-i18next'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getAffinityIconPath } from '@/shared/assets'
import { AFFINITIES, type Affinity } from '@/shared/gameData'

interface SinCostPanelProps {
  costs: Record<string, number>
}

export function SinCostPanel({ costs }: SinCostPanelProps) {
  const { t } = useTranslation('database')

  return (
    <LabeledPanel title={t('ego.sinCost')}>
      <div className="grid grid-cols-7 gap-1">
        {AFFINITIES.map((affinity: Affinity) => {
          const cost = costs[affinity] || 0
          return (
            <div key={affinity} className="flex flex-col items-center gap-1">
              <img
                src={getAffinityIconPath(affinity)}
                alt={affinity}
                className="w-6 h-6 object-contain"
              />
              <span className="text-xs">{cost}</span>
            </div>
          )
        })}
      </div>
    </LabeledPanel>
  )
}
