import { useMemo, memo } from 'react'
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

// Helper to compare arrays by value
function areArraysEqual<T>(a: T[], b: T[]): boolean {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false
  }
  return true
}

// Helper to compare equipment objects
function areEquipmentEqual(
  a: Record<string, SinnerEquipment>,
  b: Record<string, SinnerEquipment>
): boolean {
  const aKeys = Object.keys(a)
  const bKeys = Object.keys(b)
  if (aKeys.length !== bKeys.length) return false

  for (const key of aKeys) {
    const aEquip = a[key]
    const bEquip = b[key]
    if (!bEquip) return false
    if (aEquip.identity.id !== bEquip.identity.id) return false
    if (aEquip.identity.uptie !== bEquip.identity.uptie) return false
    if (aEquip.identity.level !== bEquip.identity.level) return false
    // Compare EGOs
    const aEgoKeys = Object.keys(aEquip.egos) as Array<keyof typeof aEquip.egos>
    const bEgoKeys = Object.keys(bEquip.egos) as Array<keyof typeof bEquip.egos>
    if (aEgoKeys.length !== bEgoKeys.length) return false
    for (const egoKey of aEgoKeys) {
      const aEgo = aEquip.egos[egoKey]
      const bEgo = bEquip.egos[egoKey]
      if (!bEgo || aEgo?.id !== bEgo?.id || aEgo?.threadspin !== bEgo?.threadspin) return false
    }
  }
  return true
}

/**
 * Grid of all 12 sinners with their equipped identities and deployment order.
 */
export const SinnerGrid = memo(function SinnerGrid({
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
}, (prev, next) => {
  return (
    areEquipmentEqual(prev.equipment, next.equipment) &&
    areArraysEqual(prev.deploymentOrder, next.deploymentOrder) &&
    prev.identities === next.identities &&
    prev.skillDataMap === next.skillDataMap &&
    prev.egoAffinityMap === next.egoAffinityMap
    // onToggleDeploy excluded - callback identity changes but behavior is same
  )
})

export default SinnerGrid
