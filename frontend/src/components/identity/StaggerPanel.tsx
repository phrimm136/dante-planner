import { calculateStaggerThreshold } from '@/lib/assetPaths'

interface StaggerPanelProps {
  maxHP: number
  staggerThresholds: number[]
}

export function StaggerPanel({ maxHP, staggerThresholds }: StaggerPanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Stagger</div>
      <div className="flex justify-around items-center">
        {staggerThresholds.map((threshold, index) => {
          const percentage = (threshold * 100).toFixed(0)
          const hpValue = calculateStaggerThreshold(maxHP, threshold)

          return (
            <div key={index} className="flex flex-col items-center gap-1">
              <span className="text-xs">{hpValue}</span>
              <span className="text-xs">({percentage}%)</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
