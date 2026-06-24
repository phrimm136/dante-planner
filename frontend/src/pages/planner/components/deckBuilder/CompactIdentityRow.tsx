import { memo, useMemo } from 'react'
import { SINNERS, CARD_GRID } from '@/lib/constants'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { SkillData } from './SinnerGrid'
import {
  getIdentityProfileImagePath,
  getEGOTierIconPath,
  getAttackTypeIconPath,
} from '@/lib/assetPaths'
import { cn, getDisplayFontForNumeric } from '@/lib/utils'
import colorCode from '@static/data/colorCode.json'

interface CompactIdentityRowProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  skillDataMap: Record<string, SkillData>
  onToggleDeploy: (sinnerIndex: number) => void
  readOnly?: boolean
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

/**
 * Compact grid of 12 identity thumbnails for the deck builder Identity tab.
 * Each thumbnail shows profile portrait, uptie icon, level, deployment number,
 * and 3 skill affinity boxes with attack type icons.
 *
 * memo: parent `DeckBuilderContent` re-renders on card hover due to
 * Compiler element-cache invalidation; shallow-equality memo here
 * blocks that cascade when props haven't actually changed.
 */
export const CompactIdentityRow = memo(function CompactIdentityRow({
  equipment,
  deploymentOrder,
  skillDataMap,
  onToggleDeploy,
  readOnly = false,
}: CompactIdentityRowProps) {
  const deploymentOrderMap = useMemo(() => {
    const map: Record<number, number> = {}
    deploymentOrder.forEach((sinnerIndex, orderIndex) => {
      map[sinnerIndex] = orderIndex + 1
    })
    return map
  }, [deploymentOrder])

  return (
    <div
      className="grid mx-auto"
      style={{
        gridTemplateColumns: `repeat(auto-fill, ${String(CARD_GRID.WIDTH.COMPACT_IDENTITY)}px)`,
        gap: '8px',
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
        const isDeployed = order !== null && order <= 7

        return (
          <div
            key={sinnerName}
            className="relative flex flex-col items-center gap-1"
            style={{
              width: `${String(CARD_GRID.WIDTH.COMPACT_IDENTITY)}px`,
              height: `${String(CARD_GRID.HEIGHT.COMPACT_IDENTITY)}px`,
              cursor: readOnly ? 'default' : 'pointer',
            }}
            onClick={readOnly ? undefined : () => onToggleDeploy(index)}
          >
            {/* Portrait container */}
            <div
              className="relative"
              style={{
                width: `${String(CARD_GRID.WIDTH.COMPACT_IDENTITY)}px`,
                height: `${String(CARD_GRID.WIDTH.COMPACT_IDENTITY)}px`,
              }}
            >
              {/* Profile image - dimmed when deployed */}
              <img
                src={getIdentityProfileImagePath(identityId, uptie)}
                alt={sinnerName}
                loading="lazy"
                className={cn(
                  'w-full h-full object-cover rounded-sm',
                  order !== null && 'brightness-50'
                )}
              />

              {/* Uptie icon - upper-right */}
              <div className="absolute top-0.5 right-0.5 pointer-events-none">
                <img
                  src={getEGOTierIconPath(uptie)}
                  alt={`Uptie ${String(uptie)}`}
                  className={cn('h-4', order !== null && 'brightness-50')}
                />
              </div>

              {/* Level number - lower-right */}
              <div
                className={cn(
                  'absolute bottom-0.5 right-1 pointer-events-none text-[16px] drop-shadow-[0_1px_2px_rgba(0,0,0,0.8)]',
                  order !== null && 'brightness-50'
                )}
                style={{ fontFamily: getDisplayFontForNumeric() }}
              >
                {`Lv.${String(level)}`}
              </div>

              {/* Deployment number overlay - NOT dimmed */}
              {order !== null && (
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <span
                    className={`text-[32px] ${isDeployed ? 'formation-number-deploy' : 'formation-number-backup'}`}
                    style={{ fontFamily: getDisplayFontForNumeric() }}
                  >
                    {order}
                  </span>
                </div>
              )}
            </div>

            {/* Skill affinity row - 3 colored boxes with attack type icons */}
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
                    title={`Skill ${String(idx + 1)}: ${atkType || '?'} (${affinity || '?'})`}
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
          </div>
        )
      })}
    </div>
  )
}, (prev, next) => {
  return (
    prev.equipment === next.equipment &&
    prev.deploymentOrder === next.deploymentOrder &&
    prev.skillDataMap === next.skillDataMap &&
    prev.readOnly === next.readOnly
  )
  // onToggleDeploy excluded — callback identity can change across parent renders,
  // but behavior is stable. Matches TierLevelSelector.tsx:220 precedent.
})

export default CompactIdentityRow
