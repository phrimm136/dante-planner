import React, { useMemo, memo } from 'react'
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
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

export const SinnerGrid: React.FC<SinnerGridProps> = memo(({
  equipment,
  deploymentOrder,
  identities,
  skillDataMap,
  egoAffinityMap,
  onToggleDeploy,
}) => {
  // Memoize identity lookup map
  const identityMap = useMemo(() => {
    const map: Record<string, Identity> = {}
    identities.forEach((id) => {
      map[id.id] = id
    })
    return map
  }, [identities])

  // Memoize deployment order map to avoid indexOf in render loop
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
        const sinnerEquipment = equipment[sinnerName]
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
})

export default SinnerGrid
