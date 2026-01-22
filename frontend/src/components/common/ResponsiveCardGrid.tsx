import { Children } from 'react'
import { cn } from '@/lib/utils'
import { CARD_GRID } from '@/lib/constants'

interface ResponsiveCardGridProps {
  /** Card width in pixels - determines column size */
  cardWidth: number
  /** Card height in pixels - used to calculate scaled wrapper height */
  cardHeight?: number
  /** Gap between cards in pixels (default: CARD_GRID.DEFAULT_GAP = 16px) */
  gap?: number
  /** Grid content (card components) */
  children: React.ReactNode
  /** Additional className for the grid container */
  className?: string
  /** Scale factor for cards on mobile (0-1). Applied via CSS transform */
  mobileScale?: number
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
  cardHeight,
  gap = CARD_GRID.DEFAULT_GAP,
  children,
  className,
  mobileScale = 1,
}: ResponsiveCardGridProps) {
  const scaledCardWidth = cardWidth * mobileScale
  const scaledCardHeight = cardHeight ? cardHeight * mobileScale : undefined

  return (
    <>
      {/* Mobile: Scaled grid */}
      <div
        className={cn('grid w-full lg:hidden', className)}
        style={{
          gridTemplateColumns: `repeat(auto-fill, ${String(scaledCardWidth)}px)`,
          gap: `${String(gap)}px`,
          justifyContent: 'center',
        }}
      >
        {mobileScale !== 1
          ? Children.map(children, (child) => (
              <div
                style={{
                  transform: `scale(${mobileScale})`,
                  transformOrigin: 'top left',
                  width: `${cardWidth}px`,
                  height: scaledCardHeight ? `${scaledCardHeight}px` : undefined,
                }}
              >
                {child}
              </div>
            ))
          : children}
      </div>

      {/* Desktop: Normal grid */}
      <div
        className={cn('hidden lg:grid w-full', className)}
        style={{
          gridTemplateColumns: `repeat(auto-fill, ${String(cardWidth)}px)`,
          gap: `${String(gap)}px`,
          justifyContent: 'center',
        }}
      >
        {children}
      </div>
    </>
  )
}
