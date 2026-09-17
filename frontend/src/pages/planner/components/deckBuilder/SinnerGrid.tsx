import { SINNERS, type Affinity, type AtkType } from '@/shared/gameData'
import { CardSlot } from '@/shared/cardLayout'
import { SINNER_DECK_GEOMETRY } from '../../lib/cardLayout'
import { useSinnerGridLayout } from '../../hooks/useSinnerGridLayout'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { IdentityEntity } from '@/pages/identity'
import { SinnerDeckCard } from './SinnerDeckCard'

export interface SkillData {
  affinities: Affinity[]
  atkTypes: AtkType[]
}

interface SinnerGridProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  identities: IdentityEntity[]
  skillDataMap: Record<string, SkillData>
  egoAffinityMap: Record<string, string>
  onToggleDeploy?: ((sinnerIndex: number) => void) | undefined
  readOnly?: boolean
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

/**
 * Grid of all 12 sinners with their equipped identities and deployment order.
 */
export const SinnerGrid = function SinnerGrid({
  equipment,
  deploymentOrder,
  identities,
  skillDataMap,
  egoAffinityMap,
  onToggleDeploy,
  readOnly = false,
}: SinnerGridProps) {
  const { gridStyle, mobileScale } = useSinnerGridLayout()

  // Memoize identity lookup map - only recompute when identities change
  const identityMap = (() => {
    const map: Record<string, IdentityEntity> = {}
    identities.forEach((id) => {
      map[id.id] = id
    })
    return map
  })()

  // Memoize deployment order map - only recompute when deploymentOrder changes
  const deploymentOrderMap = (() => {
    const map: Record<number, number> = {}
    deploymentOrder.forEach((sinnerIndex, orderIndex) => {
      map[sinnerIndex] = orderIndex + 1
    })
    return map
  })()

  return (
    <div className="grid mx-auto" style={gridStyle}>
      {SINNERS.map((sinnerName, index) => {
        const sinnerCode = String(index + 1)
        const sinnerEquipment = equipment[sinnerCode]
        if (!sinnerEquipment) return null

        const identityData = identityMap[sinnerEquipment.identity.id]
        const skillData = skillDataMap[sinnerEquipment.identity.id] || EMPTY_SKILL_DATA
        const order = deploymentOrderMap[index] ?? null

        return (
          <CardSlot key={sinnerName} size={SINNER_DECK_GEOMETRY.size} mobileScale={mobileScale}>
            <SinnerDeckCard
              sinnerName={sinnerName}
              sinnerIndex={index}
              equipment={sinnerEquipment}
              identityData={identityData}
              skillData={skillData}
              egoAffinityMap={egoAffinityMap}
              deploymentOrder={order}
              onToggleDeploy={onToggleDeploy}
              readOnly={readOnly}
              mobileScale={mobileScale}
            />
          </CardSlot>
        )
      })}
    </div>
  )
}
