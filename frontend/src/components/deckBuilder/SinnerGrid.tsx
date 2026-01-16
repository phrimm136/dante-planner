import { useMemo } from 'react'
import { SINNERS, type Affinity, type AtkType } from '@/lib/constants'
import type { SinnerEquipment } from '@/types/DeckTypes'
import type { Identity } from '@/types/IdentityTypes'
import { SinnerDeckCard } from './SinnerDeckCard'

export interface SkillData {
  affinities: Affinity[]
  atkTypes: AtkType[]
}

interface SinnerGridProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  identities: Identity[]
  skillDataMap: Record<string, SkillData>
  egoAffinityMap: Record<string, string>
  onToggleDeploy: (sinnerIndex: number) => void
  readOnly?: boolean
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

/**
 * Grid of all 12 sinners with their equipped identities and deployment order.
 */
export function SinnerGrid({
  equipment,
  deploymentOrder,
  identities,
  skillDataMap,
  egoAffinityMap,
  onToggleDeploy,
}: SinnerGridProps) {
  // Memoize identity lookup map - only recompute when identities change
  const identityMap = useMemo(() => {
    const map: Record<string, Identity> = {}
    identities.forEach((id) => {
      map[id.id] = id
    })
    return map
  }, [identities])

  // Memoize deployment order map - only recompute when deploymentOrder changes
  const deploymentOrderMap = useMemo(() => {
    const map: Record<number, number> = {}
    deploymentOrder.forEach((sinnerIndex, orderIndex) => {
      map[sinnerIndex] = orderIndex + 1
    })
    return map
  }, [deploymentOrder])

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
      {SINNERS.map((sinnerName, index) => {
        const sinnerCode = String(index + 1)
        const sinnerEquipment = equipment[sinnerCode]
        if (!sinnerEquipment) return null

        const identityData = identityMap[sinnerEquipment.identity.id]
        const skillData = skillDataMap[sinnerEquipment.identity.id] || EMPTY_SKILL_DATA
        const order = deploymentOrderMap[index] ?? null

        return (
          <SinnerDeckCard
            key={sinnerName}
            sinnerName={sinnerName}
            sinnerIndex={index}
            equipment={sinnerEquipment}
            identityData={identityData}
            skillData={skillData}
            egoAffinityMap={egoAffinityMap}
            deploymentOrder={order}
            onToggleDeploy={onToggleDeploy}
          />
        )
      })}
    </div>
  )
}

export default SinnerGrid
