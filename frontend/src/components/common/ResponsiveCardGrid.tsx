import { cn } from '@/lib/utils'
import { CARD_GRID } from '@/lib/constants'

interface ResponsiveCardGridProps {
  /** Card width in pixels - determines column size */
  cardWidth: number
  /** Gap between cards in pixels (default: CARD_GRID.DEFAULT_GAP = 16px) */
  gap?: number
  /** Grid content (card components) */
  children: React.ReactNode
  /** Additional className for the grid container */
  className?: string
}

/**
 * Responsive card grid with automatic column count and centered alignment.
 *
 * Features:
 * - Columns auto-adjust based on container width (CSS Grid auto-fill)
 * - Cards maintain fixed width (no stretching)
 * - Grid is centered horizontally with dynamic padding
 * - Consistent gap between all cards
 *
 * Pattern: Uses CSS Grid auto-fill with justify-content: center
 *
 * @example
 * // Identity/EGO page (160px cards)
 * <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
 *   {identities.map(id => <IdentityCard key={id} ... />)}
 * </ResponsiveCardGrid>
 *
 * // EGO Gift selection (96px cards)
 * <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
 *   {gifts.map(gift => <EGOGiftCard key={gift.id} ... />)}
 * </ResponsiveCardGrid>
 */
export function ResponsiveCardGrid({
  cardWidth,
  gap = CARD_GRID.DEFAULT_GAP,
  children,
  className,
}: ResponsiveCardGridProps) {
  return (
    <div
      className={cn('grid w-full', className)}
      style={{
        gridTemplateColumns: `repeat(auto-fill, ${String(cardWidth)}px)`,
        gap: `${String(gap)}px`,
        justifyContent: 'center',
      }}
    >
      {children}
    </div>
  )
}
