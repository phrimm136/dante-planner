import { getAffinityIconPath } from '@/lib/assetPaths'
import { AFFINITIES, type Affinity } from '@/lib/constants'

interface SinCostPanelProps {
  costs: Record<string, number>
}

export function SinCostPanel({ costs }: SinCostPanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Sin Cost</div>
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
    </div>
  )
}
