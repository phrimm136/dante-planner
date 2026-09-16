import { SINNERS, type Affinity, type AtkType } from '@/shared/gameData'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import type { CSSProperties } from 'react'
import { LG_BREAKPOINT_PX, MD_BREAKPOINT_PX, SM_BREAKPOINT_PX } from '@/lib/constants'
import { CardSlot, useSlotSizePx } from '@/shared/cardLayout'
import { SINNER_DECK_GEOMETRY, SINNER_GRID_COLUMNS, SINNER_GRID_GAP } from '../../lib/cardLayout'
import type { SinnerEquipment } from '../../types/DeckTypes'
import type { Identity, IdentityListItem } from '@/pages/identity'
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
  onToggleDeploy?: ((sinnerIndex: number) => void) | undefined
  readOnly?: boolean
}

const EMPTY_SKILL_DATA: SkillData = { affinities: [], atkTypes: [] }

/** The twelve-sinner grid's column count, slot box and CSS grid style at the current breakpoint. */
export function useSinnerGridLayout(): {
  columnWidth: number
  rowHeight: number
  mobileScale: number
  gridStyle: CSSProperties
} {
  const isLg = useIsBreakpoint('min', LG_BREAKPOINT_PX)
  const isMd = useIsBreakpoint('min', MD_BREAKPOINT_PX)
  const isSm = useIsBreakpoint('min', SM_BREAKPOINT_PX)

  const { size, mobileScale } = SINNER_DECK_GEOMETRY
  const { widthPx: columnWidth, heightPx: rowHeight } = useSlotSizePx(size, mobileScale)
  const columnCount = isLg
    ? SINNER_GRID_COLUMNS.lg
    : isMd
      ? SINNER_GRID_COLUMNS.md
      : isSm
        ? SINNER_GRID_COLUMNS.sm
        : SINNER_GRID_COLUMNS.base

  return {
    columnWidth,
    rowHeight,
    mobileScale,
    gridStyle: {
      gridTemplateColumns: `repeat(${String(columnCount)}, ${String(columnWidth)}px)`,
      gridAutoRows: `${String(rowHeight)}px`,
      columnGap: `${String(SINNER_GRID_GAP)}px`,
      rowGap: '0px',
      justifyContent: 'center',
    },
  }
}

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
      style={gridStyle}
    >
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
