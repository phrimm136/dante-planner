import type { PassiveI18n } from '@/types/EGOTypes'

interface EGOPassiveDisplayProps {
  passives: PassiveI18n[]
}

/**
 * EGOPassiveDisplay - Displays EGO passive abilities
 *
 * Shows all passives from the array without category labels or support passive section
 */
export function EGOPassiveDisplay({ passives }: EGOPassiveDisplayProps) {
  if (!passives || passives.length === 0) {
    return null
  }

  return (
    <div className="border rounded p-4 space-y-3">
      <h3 className="font-semibold text-lg">Passive</h3>
      {passives.map((passive, index) => (
        <div key={index} className="space-y-1">
          <div className="font-semibold text-sm">{passive.name}</div>
          <div className="text-sm text-muted-foreground">{passive.desc}</div>
        </div>
      ))}
    </div>
  )
}
