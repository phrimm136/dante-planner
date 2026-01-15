import { memo } from 'react'
import type { SinnerEquipment } from '@/types/DeckTypes'
import type { EGOType } from '@/types/EGOTypes'
import type { IdentityListItem } from '@/types/IdentityTypes'
import type { SkillData } from './SinnerGrid'
import { getAttackTypeIconPath, getEGOImagePath, getEGOTypeIconPath } from '@/lib/assetPaths'
import { IdentityCard } from '@/components/identity/IdentityCard'
import colorCode from '@static/data/colorCode.json'
import { getDisplayFontForNumeric } from '@/lib/utils'

interface SinnerDeckCardProps {
  sinnerName: string
  sinnerIndex: number
  equipment: SinnerEquipment
  identityData: IdentityListItem | undefined
  skillData: SkillData
  egoAffinityMap: Record<string, string>
  deploymentOrder: number | null
  onToggleDeploy: (sinnerIndex: number) => void
  readOnly?: boolean
}

const EGO_RANKS: EGOType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']

/**
 * Deck card showing equipped identity with deployment status, skills, and EGOs.
 * Uses IdentityCard for identity display with deployment order overlay.
 * Memoized to prevent re-renders when sibling sinners' data changes.
 */
export const SinnerDeckCard = memo(function SinnerDeckCard({
  sinnerIndex,
  equipment,
  identityData,
  skillData,
  egoAffinityMap,
  deploymentOrder,
  onToggleDeploy,
  readOnly = false,
}: SinnerDeckCardProps) {
  const isDeployed = deploymentOrder !== null && deploymentOrder <= 7

  // Create deployment overlay for IdentityCard
  const deploymentOverlay = deploymentOrder !== null ? (
    <div className="absolute flex inset-0 items-center justify-center">
      <div className="flex flex-col items-center -translate-y-[18px] gap-2">
        <span
          className={`w-12 h-12 flex items-center justify-center text-[48px] ${isDeployed ? 'formation-number-deploy' : 'formation-number-backup'}`}
          style={{ fontFamily: getDisplayFontForNumeric() }}
        >
          {deploymentOrder}
        </span>
        <img
          src={isDeployed ? '/images/UI/formation/selected.webp' : '/images/UI/formation/backup.webp'}
          alt={isDeployed ? 'Selected' : 'Backup'}
          className="w-37 object-contain"
        />
      </div>
    </div>
  ) : null

  // Build a minimal identity object for IdentityCard if missing
  const displayIdentity: IdentityListItem = identityData ?? {
    id: equipment.identity.id,
    name: 'Identity',
    rank: 1,
    updateDate: 0,
    unitKeywordList: [],
    skillKeywordList: [],
    attributeTypes: [],
    atkTypes: [],
    season: 0,
  }

  return (
    <div
      className="relative flex flex-col items-center gap-1 p-2 border rounded-lg transition-colors"
      onClick={readOnly ? undefined : () => onToggleDeploy(sinnerIndex)}
      style={{ cursor: readOnly ? 'default' : 'pointer' }}
    >
      {/* Identity Card with deployment overlay */}
      <IdentityCard
        identity={displayIdentity}
        uptie={equipment.identity.uptie}
        overlay={deploymentOverlay}
      />

      {/* Skill Info Row - atkType icon on affinity-colored background */}
      <div className="flex gap-1">
        {[0, 1, 2].map((idx) => {
          const affinity = skillData.affinities[idx]
          const atkType = skillData.atkTypes[idx]
          const bgColor = affinity ? (colorCode as Record<string, string>)[affinity] : undefined

          return (
            <div
              key={idx}
              className="w-7 h-7 rounded-sm flex items-center justify-center"
              style={{ backgroundColor: bgColor || 'var(--muted)' }}
              title={`Skill ${idx + 1}: ${atkType || '?'} (${affinity || '?'})`}
            >
              {atkType ? (
                <img
                  src={getAttackTypeIconPath(atkType)}
                  alt={atkType}
                  className="w-5 h-5 object-contain"
                />
              ) : (
                <div className="w-5 h-5" />
              )}
            </div>
          )
        })}
      </div>

      {/* EGO Slots Row (5 ranks) */}
      <div className="flex gap-0.5">
        {EGO_RANKS.map((rank) => {
          const equippedEgo = equipment.egos[rank]
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
                <img
                  src={getEGOTypeIconPath(rank)}
                  alt={rank}
                  className="h-4 object-cover"
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
})

export default SinnerDeckCard
