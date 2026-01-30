import { useState, useEffect } from 'react'
import { cn } from '@/lib/utils'
import { CARD_GRID } from '@/lib/constants'

interface ResponsiveCardGridProps {
  /** Card width in pixels - determines column size */
  cardWidth: number
  /** Card height in pixels - for consistent grid cell heights (optional, omit for variable-height cards) */
  cardHeight?: number
  /** Gap between cards in pixels (default: CARD_GRID.DEFAULT_GAP = 16px) */
  gap?: number
  /** Grid content (card components) */
  children: React.ReactNode
  /** Additional className for the grid container */
  className?: string
  /** Mobile scale factor (0-1) - scales column width for auto-fill calculation */
  mobileScale?: number
}

/**
 * Responsive card grid with automatic column count and centered alignment.
 *
 * Features:
 * - Columns auto-adjust based on container width (CSS Grid auto-fill)
 * - Rows created implicitly as content is added (grid-auto-rows)
 * - Cards maintain fixed width (no stretching)
 * - Grid is centered horizontally with dynamic padding
 * - Consistent gap between all cards
 * - Supports progressive rendering (incrementally adding children)
 *
 * Pattern: Uses CSS Grid auto-fill columns with implicit rows
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
  const [isDesktop, setIsDesktop] = useState(typeof window !== 'undefined' && window.innerWidth >= CARD_GRID.LG_BREAKPOINT)

  useEffect(() => {
    const handleResize = () => {
      setIsDesktop(window.innerWidth >= CARD_GRID.LG_BREAKPOINT)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const scaledCardWidth = cardWidth * mobileScale
  const columnWidth = isDesktop ? cardWidth : scaledCardWidth

  // Only apply fixed row height if cardHeight is provided
  const gridStyle: React.CSSProperties = {
    gridTemplateColumns: `repeat(auto-fill, ${String(columnWidth)}px)`,
    gap: `${String(gap)}px`,
    justifyContent: 'center',
  }

  if (cardHeight !== undefined) {
    const scaledCardHeight = cardHeight * mobileScale
    const rowHeight = isDesktop ? cardHeight : scaledCardHeight
    gridStyle.gridAutoRows = `${String(rowHeight)}px`
  }

  return (
    <div
      className={cn('grid', className)}
      style={gridStyle}
    >
      {children}
    </div>
  )
}
