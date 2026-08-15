import { SINNERS, type Affinity, type AtkType } from '@/shared/gameData'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { CARD_GRID } from '@/lib/constants'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { Identity, IdentityListItem } from '@/pages/identity'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
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
  onToggleDeploy?: (sinnerIndex: number) => void
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
  const isLg = useIsBreakpoint('min', CARD_GRID.LG_BREAKPOINT)
  const isMd = useIsBreakpoint('min', CARD_GRID.MD_BREAKPOINT)
  const isSm = useIsBreakpoint('min', CARD_GRID.SM_BREAKPOINT)

  // Calculate scale and dimensions
  const isDesktop = isLg
  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD
  const scale = isDesktop ? 1 : mobileScale
  const scaledWidth = CARD_GRID.WIDTH.IDENTITY * scale
  const scaledHeight = CARD_GRID.HEIGHT.DECK * scale

  const columnCount = isLg ? 6 : isMd ? 4 : isSm ? 3 : 2

  // Memoize identity lookup map - only recompute when identities change
  const identityMap = (() => {
    const map: Record<string, IdentityListItem> = {}
    identities.forEach((id) => {
      map[id.id] = { ...id, battleKeywordList: (id as IdentityListItem).battleKeywordList ?? [] }
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
}
