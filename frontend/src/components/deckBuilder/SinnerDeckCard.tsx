import React, { useCallback, memo } from 'react'
import type { SinnerEquipment } from '@/types/DeckTypes'
import type { EgoType } from '@/types/EGOTypes'
import type { Identity } from '@/types/IdentityTypes'
import type { SkillData } from './SinnerGrid'
import {
  getIdentityInfoImagePath,
  getIdentityImageFallbackPath,
  getUptieFramePath,
  getAttackTypeIconPath,
  getEGOImagePath,
} from '@/lib/assetPaths'
import colorCode from '@static/data/colorCode.json'

interface SinnerDeckCardProps {
  sinnerName: string
  sinnerIndex: number
  equipment: SinnerEquipment
  identityData: Identity | undefined
  skillData: SkillData
  egoAffinityMap: Record<string, string>
  deploymentOrder: number | null // null if not deployed
  onToggleDeploy: (sinnerIndex: number) => void
}

const EGO_RANKS: EgoType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']

const RANK_DISPLAY_CHARS: Record<EgoType, string> = {
  ZAYIN: 'ז',
  TETH: 'ט',
  HE: 'ה',
  WAW: 'ו',
  ALEPH: 'א',
}

export const SinnerDeckCard: React.FC<SinnerDeckCardProps> = memo(({
  sinnerIndex,
  equipment,
  identityData,
  skillData,
  egoAffinityMap,
  deploymentOrder,
  onToggleDeploy,
}) => {
  const handleClick = useCallback(() => {
    onToggleDeploy(sinnerIndex)
  }, [onToggleDeploy, sinnerIndex])

  const isDeployed = deploymentOrder !== null && deploymentOrder <= 7
  const starRating = identityData?.rank ?? 1

  return (
    <div
      className="relative flex flex-col items-center gap-1 p-2 border rounded-lg cursor-pointer transition-colors"
      onClick={handleClick}
    >

      {/* Identity Card Section */}
      <div className="relative w-40 h-56">
        {/* Identity Image */}
        <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
          <img
            src={getIdentityInfoImagePath(equipment.identity.id, equipment.identity.uptie)}
            onError={(e) => {
              const target = e.currentTarget
              if (!target.dataset.fallback) {
                target.dataset.fallback = 'true'
                target.src = getIdentityImageFallbackPath(equipment.identity.id)
              }
            }}
            alt={identityData?.name ?? 'Identity'}
            className="w-[84%] h-[93%] object-cover"
            style={{ clipPath: 'polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)' }}
          />
        </div>
        {/* Uptie Frame */}
        <img
          src={getUptieFramePath(starRating, equipment.identity.uptie)}
          alt={`Uptie ${equipment.identity.uptie}`}
          className="absolute inset-0 w-full h-full object-contain pointer-events-none"
        />
        {/* Deployment Order Badge - Centered */}
        {deploymentOrder !== null && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="flex flex-col items-center">
              {/* Order Number */}
              <div
                className={`w-12 h-12 rounded-full flex items-center justify-center text-xl font-bold shadow-lg ${
                  isDeployed
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground'
                }`}
              >
                #{deploymentOrder}
              </div>
              {/* Selected/Backup Icon */}
              <img
                src={isDeployed ? '/images/UI/formation/selected.webp' : '/images/UI/formation/backup.webp'}
                alt={isDeployed ? 'Selected' : 'Backup'}
                className="w-24 h-24 object-contain"
              />
            </div>
          </div>
        )}
      </div>

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
})

export default SinnerDeckCard
