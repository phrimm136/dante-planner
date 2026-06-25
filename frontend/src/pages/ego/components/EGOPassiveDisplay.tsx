import { FLAVOR_TEXT_COLOR } from '@/lib/constants'
import type { EGOPassiveI18n } from '../types/EGOTypes'

interface EGOPassiveDisplayProps {
  passives: EGOPassiveI18n[]
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
      {passives.map((passive, index) => (
        <div key={index} className="space-y-1">
          <div className="font-semibold text-sm">{passive.name}</div>
          <div className="text-sm text-muted-foreground">{passive.desc}</div>
          {passive.flavor && (
            <p
              data-testid="passive-flavor"
              className="text-sm italic whitespace-pre-line"
              style={{ color: FLAVOR_TEXT_COLOR }}
            >
              {passive.flavor}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}
