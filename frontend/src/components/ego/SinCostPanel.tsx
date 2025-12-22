import { getAffinityIconPath } from '@/lib/assetPaths'
import { AFFINITIES, type Affinity } from '@/lib/constants'

interface SinCostPanelProps {
  costs: number[]
}

export function SinCostPanel({ costs }: SinCostPanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Sin Cost</div>
      <div className="grid grid-cols-7 gap-1">
        {AFFINITIES.map((affinity: Affinity, index: number) => (
          <div key={affinity} className="flex flex-col items-center gap-1">
            <img
              src={getAffinityIconPath(affinity)}
              alt={affinity}
              className="w-6 h-6 object-contain"
            />
            <span className="text-xs">{costs[index] || 0}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
