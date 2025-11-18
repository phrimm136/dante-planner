import { getResistanceInfo, getSinIconPath } from '@/lib/identityUtils'
import { SINS } from '@/lib/constants'

interface SinResistancePanelProps {
  resistances: number[]
}

export function SinResistancePanel({ resistances }: SinResistancePanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Sin Resistance</div>
      <div className="grid grid-cols-7 gap-1">
        {SINS.map((sin, index) => {
          const resistInfo = getResistanceInfo(resistances[index] || 1.0)
          return (
            <div key={sin} className="flex flex-col items-center gap-1">
              <img
                src={getSinIconPath(sin)}
                alt={sin}
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
