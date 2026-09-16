import type { CSSProperties } from 'react'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { SM_BREAKPOINT_PX } from '@/lib/constants'
import { useSlotSizePx } from '@/shared/cardLayout'
import {
  SINNER_SKILL_GEOMETRY,
  SKILL_REPLACEMENT_COLUMNS,
  SKILL_REPLACEMENT_GRID_GAP,
} from '../lib/cardLayout'

/** The twelve-sinner skill grid's column count, slot box and CSS grid style at the current breakpoint. */
export function useSkillReplacementLayout(): {
  columnWidth: number
  rowHeight: number
  mobileScale: number
  gridStyle: CSSProperties
} {
  const isSm = useIsBreakpoint('min', SM_BREAKPOINT_PX)

  const { size, mobileScale } = SINNER_SKILL_GEOMETRY
  const { widthPx: columnWidth, heightPx: rowHeight } = useSlotSizePx(size, mobileScale)
  const columnCount = isSm ? SKILL_REPLACEMENT_COLUMNS.wide : SKILL_REPLACEMENT_COLUMNS.narrow

  return {
    columnWidth,
    rowHeight,
    mobileScale,
    gridStyle: {
      gridTemplateColumns: `repeat(${String(columnCount)}, ${String(columnWidth)}px)`,
      gridAutoRows: `${String(rowHeight)}px`,
      gap: `${String(SKILL_REPLACEMENT_GRID_GAP)}px`,
      justifyContent: 'center',
    },
  }
}
