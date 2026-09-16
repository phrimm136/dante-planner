import type { CSSProperties } from 'react'

import { SINNERS, getAttributeColors } from '@/shared/gameData'
import { CARD_MOBILE_SCALE_NONE, MAX_DEPLOYED_ORDER } from '@/lib/constants'
import { COMPACT_IDENTITY_GEOMETRY } from '../../lib/cardLayout'
import { CardSlot } from '@/shared/cardLayout'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { SkillData } from './SinnerGrid'
import {
  getIdentityProfileImagePath,
  getEGOTierIconPath,
  getAttackTypeIconPath,
} from '@/shared/assets'
import { cn, getDisplayFontForNumeric } from '@/lib/utils'
import { COMPACT_IDENTITY_CARD, COMPACT_IDENTITY_GRID_GAP, cqw } from '../../lib/cardLayout'

interface CompactIdentityRowProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  skillDataMap: Record<string, SkillData>
  onToggleDeploy?: (sinnerIndex: number) => void
  readOnly?: boolean
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

const CELL_STYLE: CSSProperties = {
  gap: cqw(COMPACT_IDENTITY_CARD.rowGap),
}

const SKILL_BOX_STYLE: CSSProperties = {
  width: cqw(COMPACT_IDENTITY_CARD.skillBox),
  height: cqw(COMPACT_IDENTITY_CARD.skillBox),
}

const ATK_ICON_STYLE: CSSProperties = {
  width: cqw(COMPACT_IDENTITY_CARD.atkIcon),
  height: cqw(COMPACT_IDENTITY_CARD.atkIcon),
}

/**
 * Compact grid of 12 identity thumbnails for the deck builder Identity tab.
 * Each thumbnail shows profile portrait, uptie icon, level, deployment number,
 * and 3 skill affinity boxes with attack type icons.
 */
export const CompactIdentityRow = function CompactIdentityRow({
  equipment,
  deploymentOrder,
  skillDataMap,
  onToggleDeploy,
  readOnly = false,
}: CompactIdentityRowProps) {
  const deploymentOrderMap = (() => {
    const map: Record<number, number> = {}
    deploymentOrder.forEach((sinnerIndex, orderIndex) => {
      map[sinnerIndex] = orderIndex + 1
    })
    return map
  })()

  return (
    <div
      className="grid mx-auto"
      style={{
        gridTemplateColumns: `repeat(auto-fill, ${String(COMPACT_IDENTITY_GEOMETRY.size.widthPx)}px)`,
        gap: `${String(COMPACT_IDENTITY_GRID_GAP)}px`,
        justifyContent: 'center',
      }}
    >
      {SINNERS.map((sinnerName, index) => {
        const sinnerCode = String(index + 1)
        const sinnerEquipment = equipment[sinnerCode]
        if (!sinnerEquipment) return null

        const identityId = sinnerEquipment.identity.id
        const uptie = sinnerEquipment.identity.uptie
        const level = sinnerEquipment.identity.level
        const skillData = skillDataMap[identityId] || EMPTY_SKILL_DATA
        const order = deploymentOrderMap[index] ?? null
        const isDeployed = order !== null && order <= MAX_DEPLOYED_ORDER

        return (
          <CardSlot
            key={sinnerName}
            size={COMPACT_IDENTITY_GEOMETRY.size}
            mobileScale={CARD_MOBILE_SCALE_NONE}
          >
            <button
              type="button"
              className="relative w-full h-full flex flex-col items-center"
              style={{ ...CELL_STYLE, cursor: readOnly ? 'default' : 'pointer' }}
              disabled={readOnly || !onToggleDeploy}
              aria-pressed={order !== null}
              onClick={() => onToggleDeploy?.(index)}
            >
              {/* Portrait container */}
              <div className="relative w-full aspect-square">
                {/* Profile image - dimmed when deployed */}
                <img
                  src={getIdentityProfileImagePath(identityId, uptie)}
                  alt={sinnerName}
                  loading="lazy"
                  className={cn(
                    'w-full h-full object-cover rounded-sm',
                    order !== null && 'brightness-50',
                  )}
                />

                {/* Uptie icon - upper-right */}
                <div
                  className="absolute pointer-events-none"
                  style={{
                    top: cqw(COMPACT_IDENTITY_CARD.uptieInset),
                    right: cqw(COMPACT_IDENTITY_CARD.uptieInset),
                  }}
                >
                  <img
                    src={getEGOTierIconPath(uptie)}
                    alt={`Uptie ${String(uptie)}`}
                    className={cn(order !== null && 'brightness-50')}
                    style={{ height: cqw(COMPACT_IDENTITY_CARD.uptieIcon) }}
                  />
                </div>

                {/* Level number - lower-right */}
                <div
                  className={cn(
                    'absolute pointer-events-none drop-shadow-[0_1px_2px_rgba(0,0,0,0.8)]',
                    order !== null && 'brightness-50',
                  )}
                  style={{
                    bottom: cqw(COMPACT_IDENTITY_CARD.levelBottom),
                    right: cqw(COMPACT_IDENTITY_CARD.levelRight),
                    fontSize: cqw(COMPACT_IDENTITY_CARD.levelFontSize),
                    fontFamily: getDisplayFontForNumeric(),
                  }}
                >
                  {`Lv.${String(level)}`}
                </div>

                {/* Deployment number overlay - NOT dimmed */}
                {order !== null && (
                  <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                    <span
                      className={isDeployed ? 'formation-number-deploy' : 'formation-number-backup'}
                      style={{
                        fontSize: cqw(COMPACT_IDENTITY_CARD.orderFontSize),
                        fontFamily: getDisplayFontForNumeric(),
                      }}
                    >
                      {order}
                    </span>
                  </div>
                )}
              </div>

              {/* Skill affinity row - 3 colored boxes with attack type icons */}
              <div className="flex" style={{ gap: cqw(COMPACT_IDENTITY_CARD.skillGap) }}>
                {[0, 1, 2].map((idx) => {
                  const affinity = skillData.affinities[idx]
                  const atkType = skillData.atkTypes[idx]
                  const bgColor = affinity ? getAttributeColors(affinity).primary : undefined

                  return (
                    <div
                      key={idx}
                      className="rounded-sm flex items-center justify-center"
                      style={{ ...SKILL_BOX_STYLE, backgroundColor: bgColor || 'var(--muted)' }}
                      title={`Skill ${String(idx + 1)}: ${atkType || '?'} (${affinity || '?'})`}
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
            </button>
          </CardSlot>
        )
      })}
    </div>
  )
}
