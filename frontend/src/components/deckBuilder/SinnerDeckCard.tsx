import type { SinnerEquipment } from '@/types/DeckTypes'
import type { EGOType } from '@/types/EGOTypes'
import type { Identity } from '@/types/IdentityTypes'
import type { SkillData } from './SinnerGrid'
import { getAttackTypeIconPath, getEGOImagePath } from '@/lib/assetPaths'
import { IdentityCard } from '@/components/identity/IdentityCard'
import colorCode from '@static/data/colorCode.json'

interface SinnerDeckCardProps {
  sinnerName: string
  sinnerIndex: number
  equipment: SinnerEquipment
  identityData: Identity | undefined
  skillData: SkillData
  egoAffinityMap: Record<string, string>
  deploymentOrder: number | null
  onToggleDeploy: (sinnerIndex: number) => void
}

const EGO_RANKS: EGOType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']

const RANK_DISPLAY_CHARS: Record<EGOType, string> = {
  ZAYIN: 'ז',
  TETH: 'ט',
  HE: 'ה',
  WAW: 'ו',
  ALEPH: 'א',
}

/**
 * Deck card showing equipped identity with deployment status, skills, and EGOs.
 * Uses IdentityCard for identity display with deployment order overlay.
 */
export function SinnerDeckCard({
  sinnerIndex,
  equipment,
  identityData,
  skillData,
  egoAffinityMap,
  deploymentOrder,
  onToggleDeploy,
}: SinnerDeckCardProps) {
  const isDeployed = deploymentOrder !== null && deploymentOrder <= 7

  // Create deployment overlay for IdentityCard
  const deploymentOverlay = deploymentOrder !== null ? (
    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
      <div className="flex flex-col items-center">
        <div
          className={`w-12 h-12 rounded-full flex items-center justify-center text-xl font-bold shadow-lg ${
            isDeployed
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-muted-foreground'
          }`}
        >
          #{deploymentOrder}
        </div>
        <img
          src={isDeployed ? '/images/UI/formation/selected.webp' : '/images/UI/formation/backup.webp'}
          alt={isDeployed ? 'Selected' : 'Backup'}
          className="w-24 h-24 object-contain"
        />
      </div>
    </div>
  ) : null

  // Build a minimal identity object for IdentityCard if missing
  const displayIdentity: Identity = identityData ?? {
    id: equipment.identity.id,
    name: 'Identity',
    rank: 1,
    unitKeywordList: [],
    skillKeywordList: [],
  }

  return (
    <div
      className="relative flex flex-col items-center gap-1 p-2 border rounded-lg cursor-pointer transition-colors"
      onClick={() => onToggleDeploy(sinnerIndex)}
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
                <span className="text-[10px] text-muted-foreground font-semibold">
                  {RANK_DISPLAY_CHARS[rank]}
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default SinnerDeckCard
