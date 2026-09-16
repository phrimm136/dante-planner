import { cn } from '@/lib/utils'
import { CARD_GAP_PX } from '@/lib/constants'
import { useSlotSizePx, type GridRowHeight, type CardSizePx } from '@/shared/cardLayout'

interface ResponsiveCardGridProps {
  /** The card's box */
  size: CardSizePx
  /** Whether rows take the card's box or their own content (default: 'slot') */
  rows?: GridRowHeight
  /** Gap between cards in pixels (default: CARD_GAP_PX = 16px) */
  gap?: number
  /** Grid content (card components) */
  children: React.ReactNode
  /** Additional className for the grid container */
  className?: string
  /** Mobile scale factor (0-1) - scales column width for auto-fill calculation */
  mobileScale?: number
  /** Handle on the grid element itself, for readers of its resolved track geometry */
  ref?: React.Ref<HTMLDivElement>
}

/**
 * Responsive card grid with automatic column count and centered alignment.
 *
 * Features:
 * - Columns auto-adjust based on container width (CSS Grid auto-fill)
 * - Rows are pinned to the card's box, or left to their content
 * - Cards maintain fixed width (no stretching)
 * - Grid is centered horizontally with dynamic padding
 * - Consistent gap between all cards
 * - Supports progressive rendering (incrementally adding children)
 *
 * Pattern: Uses CSS Grid auto-fill columns with implicit rows
 *
 * @example
 * <ResponsiveCardGrid size={IDENTITY_GEOMETRY.size}>
 *   {identities.map(id => <IdentityCard key={id} ... />)}
 * </ResponsiveCardGrid>
 */
export function ResponsiveCardGrid({
  size,
  rows = 'slot',
  gap = CARD_GAP_PX,
  children,
  className,
  mobileScale = 1,
  ref,
}: ResponsiveCardGridProps) {
  const { widthPx: columnWidthPx, heightPx: rowHeightPx } = useSlotSizePx(size, mobileScale)

  const gridStyle: React.CSSProperties = {
    gridTemplateColumns: `repeat(auto-fill, ${String(columnWidthPx)}px)`,
    gap: `${String(gap)}px`,
    justifyContent: 'center',
    ...(rows === 'slot' && {
      gridAutoRows: `${String(rowHeightPx)}px`,
    }),
  }

  return (
    <div ref={ref} className={cn('grid', className)} style={gridStyle}>
      {children}
    </div>
  )
}
