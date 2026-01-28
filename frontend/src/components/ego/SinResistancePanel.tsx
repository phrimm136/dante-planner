import { useTranslation } from 'react-i18next'

import { getResistanceInfo, getAffinityIconPath } from '@/lib/assetPaths'
import { AFFINITIES, type Affinity } from '@/lib/constants'

interface SinResistancePanelProps {
  resistances: Record<string, number>
}

export function SinResistancePanel({ resistances }: SinResistancePanelProps) {
  const { t } = useTranslation('database')

  return (
    <div className="border rounded p-3 space-y-2 h-full">
      <div className="font-semibold text-sm text-center">{t('identity.resist.sinResistance')}</div>
      <div className="grid grid-cols-7 gap-1">
        {AFFINITIES.map((affinity: Affinity) => {
          const resistValue = resistances[affinity] ?? 1.0
          const resistInfo = getResistanceInfo(resistValue)
          return (
            <div key={affinity} className="flex flex-col items-center gap-1">
              <img
                src={getAffinityIconPath(affinity)}
                alt={affinity}
                className="w-6 h-6 object-contain"
              />
              <div className="flex flex-col items-center">
                <span className={`text-xs ${resistInfo.color}`}>
                  {t(`identity.resist.${resistInfo.categoryKey}`)}
                </span>
                <span className={`text-xs ${resistInfo.color}`}>(x{resistInfo.value})</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
