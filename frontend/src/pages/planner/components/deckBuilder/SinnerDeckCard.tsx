import type { CSSProperties } from 'react'

import { getAttributeColors } from '@/shared/gameData'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { EgoType } from '@/shared/gameData'
import type { IdentityListItem } from '@/pages/identity'
import type { SkillData } from './SinnerGrid'
import { getAttackTypeIconPath, getEGOImagePath, getEGOTypeIconPath } from '@/shared/assets'
import { FORMATION_SLOT_DIM, FormationBadge, IdentityCard } from '@/pages/identity'
import type { FormationSlotState } from '@/pages/identity'
import { CardSlot, IDENTITY_GEOMETRY } from '@/shared/cardLayout'
import { MAX_DEPLOYED_ORDER } from '@/lib/constants'
import { DECK_CARD, cqw } from '../../lib/cardLayout'

interface SinnerDeckCardProps {
  sinnerName: string
  sinnerIndex: number
  equipment: SinnerEquipment
  identityData: IdentityListItem | undefined
  skillData: SkillData
  egoAffinityMap: Record<string, string>
  deploymentOrder: number | null
  /** The share of the desktop width the identity card takes below the desktop breakpoint */
  mobileScale: number
  onToggleDeploy?: ((sinnerIndex: number) => void) | undefined
  readOnly?: boolean
}

const EGO_RANKS: EgoType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']

const ROOT_STYLE: CSSProperties = {
  padding: `${cqw(DECK_CARD.padding)} ${cqw(DECK_CARD.padding)} 0`,
  gap: cqw(DECK_CARD.rowGap),
}

const SLOT_BOX_STYLE: CSSProperties = {
  width: cqw(DECK_CARD.skillBox),
  height: cqw(DECK_CARD.skillBox),
}

const ATK_ICON_STYLE: CSSProperties = {
  width: cqw(DECK_CARD.atkIcon),
  height: cqw(DECK_CARD.atkIcon),
}

/**
 * Deck card showing equipped identity with deployment status, skills, and EGOs.
 * Uses IdentityCard for identity display with deployment order overlay.
 */
export const SinnerDeckCard = function SinnerDeckCard({
  sinnerIndex,
  equipment,
  identityData,
  skillData,
  egoAffinityMap,
  deploymentOrder,
  mobileScale,
  onToggleDeploy,
  readOnly = false,
}: SinnerDeckCardProps) {
  const isDeployed = deploymentOrder !== null && deploymentOrder <= MAX_DEPLOYED_ORDER
  const slotState: FormationSlotState = isDeployed ? 'deployed' : 'backup'

  const deploymentOverlay =
    deploymentOrder !== null ? <FormationBadge state={slotState} order={deploymentOrder} /> : null

  // Build a minimal identity object for IdentityCard if missing
  const displayIdentity: IdentityListItem = identityData ?? {
    id: equipment.identity.id,
    name: 'Identity',
    rank: 1,
    updateDate: 0,
    unitKeywordList: [],
    skillKeywordList: [],
    battleKeywordList: [],
    attributeTypes: [],
    atkTypes: [],
    defenseTypes: [],
    season: 0,
  }

  return (
    <div
      className="relative w-full flex flex-col items-center transition-colors"
      style={ROOT_STYLE}
    >
      {/* Identity Card with deployment overlay - click here to toggle deploy */}
      <button
        type="button"
        className="group"
        disabled={readOnly || !onToggleDeploy}
        aria-pressed={deploymentOrder !== null}
        onClick={() => onToggleDeploy?.(sinnerIndex)}
        style={{ cursor: readOnly ? 'default' : 'pointer' }}
      >
        <CardSlot size={IDENTITY_GEOMETRY.size} mobileScale={mobileScale}>
          <IdentityCard
            identity={displayIdentity}
            uptie={equipment.identity.uptie}
            level={equipment.identity.level}
            dim={deploymentOrder === null ? undefined : FORMATION_SLOT_DIM}
            overlay={deploymentOverlay}
          />
        </CardSlot>
      </button>

      {/* Skill Info Row - atkType icon on affinity-colored background */}
      <div className="flex" style={{ gap: cqw(DECK_CARD.skillGap) }}>
        {[0, 1, 2].map((idx) => {
          const affinity = skillData.affinities[idx]
          const atkType = skillData.atkTypes[idx]
          const bgColor = affinity ? getAttributeColors(affinity).primary : undefined

          return (
            <div
              key={idx}
              className="rounded-sm flex items-center justify-center"
              style={{ ...SLOT_BOX_STYLE, backgroundColor: bgColor || 'var(--muted)' }}
              title={`Skill ${idx + 1}: ${atkType || '?'} (${affinity || '?'})`}
            >
              {atkType ? (
                <img
                  src={getAttackTypeIconPath(atkType)}
                  alt={atkType}
                  className="object-contain"
                  style={ATK_ICON_STYLE}
                />
              ) : (
                <div style={ATK_ICON_STYLE} />
              )}
            </div>
          )
        })}
      </div>

      {/* EGO Slots Row (5 ranks) */}
      <div className="flex" style={{ gap: cqw(DECK_CARD.egoGap) }}>
        {EGO_RANKS.map((rank) => {
          const equippedEgo = equipment.egos[rank]
          const egoAffinity = equippedEgo ? egoAffinityMap[equippedEgo.id] : undefined
          const egoBgColor = egoAffinity ? getAttributeColors(egoAffinity).primary : undefined
          return (
            <div
              key={rank}
              className="rounded-sm border border-border flex items-center justify-center overflow-hidden"
              style={{
                width: cqw(DECK_CARD.egoBox),
                height: cqw(DECK_CARD.egoBox),
                backgroundColor: egoBgColor || 'var(--muted)',
              }}
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
                  className="object-cover"
                  style={{ height: cqw(DECK_CARD.egoFallbackIcon) }}
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
