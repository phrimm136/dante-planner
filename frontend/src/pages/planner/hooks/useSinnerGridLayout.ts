import type { CSSProperties } from 'react'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { LG_BREAKPOINT_PX, MD_BREAKPOINT_PX, SM_BREAKPOINT_PX } from '@/lib/constants'
import { useSlotSizePx } from '@/shared/cardLayout'
import { SINNER_DECK_GEOMETRY, SINNER_GRID_COLUMNS, SINNER_GRID_GAP } from '../lib/cardLayout'

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
