import { SINNERS } from '@/shared/gameData'
import { getEGOImagePath, getEGOTypeIconPath } from '@/shared/assets'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { EGOType } from '@/pages/ego'
import colorCode from '@static/data/colorCode.json'

interface CompactEgoGridProps {
  equipment: Record<string, SinnerEquipment>
  egoAffinityMap: Record<string, string>
}

const EGO_RANKS: EGOType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']

/**
 * Compact grid showing 12 sinner cells, each with 5 ego rank slots.
 * Displayed when Entity toggle is on EGO tab.
 * Read-only display — ego equipping happens through the selection list below.
 */
export function CompactEgoGrid({ equipment, egoAffinityMap }: CompactEgoGridProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 w-fit mx-auto gap-2">
      {SINNERS.map((_sinnerName, index) => {
        const sinnerCode = String(index + 1)
        const sinnerEquipment = equipment[sinnerCode]
        if (!sinnerEquipment) return null

        return (
          <div key={sinnerCode} className="flex gap-0.5 justify-center">
            {EGO_RANKS.map((rank) => {
              const equippedEgo = sinnerEquipment.egos[rank]
              const egoBgColor = equippedEgo
                ? (colorCode as Record<string, string>)[egoAffinityMap[equippedEgo.id]]
                : undefined
              return (
                <div
                  key={rank}
                  className="w-7 h-7 rounded-sm border border-border flex items-center justify-center overflow-hidden"
                  style={{ backgroundColor: egoBgColor || 'var(--muted)' }}
                  title={rank}
                >
                  {equippedEgo ? (
                    <img
                      src={getEGOImagePath(equippedEgo.id)}
                      alt={rank}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <img src={getEGOTypeIconPath(rank)} alt={rank} className="h-4 object-cover" />
                  )}
                </div>
              )
            })}
          </div>
        )
      })}
    </div>
  )
}

export default CompactEgoGrid
