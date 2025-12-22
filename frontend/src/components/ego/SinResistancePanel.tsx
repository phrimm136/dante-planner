import { getResistanceInfo, getAffinityIconPath } from '@/lib/assetPaths'
import { AFFINITIES, type Affinity } from '@/lib/constants'

interface SinResistancePanelProps {
  resistances: number[]
}

export function SinResistancePanel({ resistances }: SinResistancePanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Sin Resistance</div>
      <div className="grid grid-cols-7 gap-1">
        {AFFINITIES.map((affinity: Affinity, index: number) => {
          const resistInfo = getResistanceInfo(resistances[index] || 1.0)
          return (
            <div key={affinity} className="flex flex-col items-center gap-1">
              <img
                src={getAffinityIconPath(affinity)}
                alt={affinity}
                className="w-6 h-6 object-contain"
              />
              <div className="flex flex-col items-center">
                <span className={`text-xs ${resistInfo.color}`}>{resistInfo.category}</span>
                <span className={`text-xs ${resistInfo.color}`}>(x{resistInfo.value})</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
