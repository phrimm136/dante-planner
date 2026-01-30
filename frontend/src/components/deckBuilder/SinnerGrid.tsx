import { useMemo, memo, useState, useEffect } from 'react'
import { SINNERS, CARD_GRID, type Affinity, type AtkType } from '@/lib/constants'
import type { SinnerEquipment } from '@/types/DeckTypes'
import type { Identity } from '@/types/IdentityTypes'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
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
  readOnly = false,
}: SinnerGridProps) {
  // Breakpoint detection for scaling and column count
  const [windowWidth, setWindowWidth] = useState(
    typeof window !== 'undefined' ? window.innerWidth : 1024
  )

  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Calculate scale and dimensions
  const isDesktop = windowWidth >= CARD_GRID.LG_BREAKPOINT
  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD
  const scale = isDesktop ? 1 : mobileScale
  const scaledWidth = CARD_GRID.WIDTH.IDENTITY * scale
  const scaledHeight = CARD_GRID.HEIGHT.DECK * scale

  // Calculate column count based on breakpoint
  const getColumnCount = () => {
    if (windowWidth >= 1024) return 6 // lg
    if (windowWidth >= 768) return 4  // md
    if (windowWidth >= 640) return 3  // sm
    return 2 // default
  }

  const columnCount = getColumnCount()

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
    <div
      className="grid mx-auto"
      style={{
        gridTemplateColumns: `repeat(${columnCount}, ${scaledWidth}px)`,
        gridAutoRows: `${scaledHeight}px`,
        gap: '8px',
        justifyContent: 'center',
      }}
    >
      {SINNERS.map((sinnerName, index) => {
        const sinnerCode = String(index + 1)
        const sinnerEquipment = equipment[sinnerCode]
        if (!sinnerEquipment) return null

        const identityData = identityMap[sinnerEquipment.identity.id]
        const skillData = skillDataMap[sinnerEquipment.identity.id] || EMPTY_SKILL_DATA
        const order = deploymentOrderMap[index] ?? null

        return (
          <ScaledCardWrapper
            key={sinnerName}
            mobileScale={mobileScale}
            cardWidth={CARD_GRID.WIDTH.IDENTITY}
            cardHeight={CARD_GRID.HEIGHT.DECK}
          >
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
            />
          </ScaledCardWrapper>
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
