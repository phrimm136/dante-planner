import { BASE_LEVEL } from '@/lib/constants'

interface StatusPanelProps {
  hp: number
  minSpeed: number
  maxSpeed: number
  defense: string
}

export function StatusPanel({ hp, minSpeed, maxSpeed, defense }: StatusPanelProps) {
  // Parse defense modifier (e.g., "+5" -> 5, "-3" -> -3)
  const defenseModifier = parseInt(defense)
  const calculatedDefense = BASE_LEVEL + defenseModifier

  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Status</div>
      <div className="flex justify-around items-center">
        {/* HP */}
        <div className="flex flex-col items-center gap-1">
          <img src="/images/UI/identity/hp.webp" alt="HP" className="w-6 h-6 object-contain" />
          <span className="text-xs">{hp}</span>
        </div>

        {/* Speed */}
        <div className="flex flex-col items-center gap-1">
          <img
            src="/images/UI/identity/speed.webp"
            alt="Speed"
            className="w-6 h-6 object-contain"
          />
          <span className="text-xs">
            {minSpeed}-{maxSpeed}
          </span>
        </div>

        {/* Defense */}
        <div className="flex flex-col items-center gap-1">
          <img
            src="/images/UI/identity/defense.webp"
            alt="Defense"
            className="w-6 h-6 object-contain"
          />
          <span className="text-xs">
            {calculatedDefense} ({defense})
          </span>
        </div>
      </div>
    </div>
  )
}
