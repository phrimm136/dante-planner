import { Skeleton } from '@/components/ui/skeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { CARD_GAP_PX, SECTION_STYLES } from '@/lib/constants'
import { useSlotSizePx, type CardGeometry } from '@/shared/cardLayout'
import { useViewportFillCount } from '@/components/hooks/useViewportFillCount'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

interface ListPageSkeletonProps {
  /** The card's sizing, which the placeholder boxes take */
  geometry: CardGeometry
  /** Number of filter section skeletons in sidebar */
  filterCount?: number
}

/**
 * ListPageSkeleton - Loading placeholder for filter + list pages
 *
 * Matches FilterPageLayout structure:
 * - Desktop: Sidebar (280px) + Content grid
 * - Mobile: Stacked layout with filter header placeholder
 *
 * Shows pulsing skeleton for filter sections and card grid.
 */
export function ListPageSkeleton({ geometry, filterCount = 5 }: ListPageSkeletonProps) {
  const { size, mobileScale } = geometry
  const slotSize = useSlotSizePx(size, mobileScale)
  const { widthPx, heightPx } = slotSize
  const cardCount = useViewportFillCount(slotSize, CARD_GAP_PX)

  return (
    <div data-slot="page-skeleton" className="flex flex-col lg:flex-row gap-6">
      {/* Sidebar skeleton - hidden on mobile, visible on lg+ */}
      <aside className="hidden lg:block w-70 shrink-0">
        <div className="rounded-lg border bg-card p-3 space-y-2">
          {/* Filter section skeletons */}
          {Array.from({ length: filterCount }).map((_, i) => (
            <div key={i} className="space-y-2">
              {/* FilterSection's text-xs title */}
              <TextSkeleton size="xs" width="sm" />
              {/* FilterSectionList's dropdown fallback box */}
              <div className="h-10 w-full rounded-md bg-muted" />
            </div>
          ))}
          {/* SearchBar, which is h-14 */}
          <div className="h-14 w-full rounded-md bg-muted" />
          {/* Reset All, a size="sm" Button */}
          <div className="h-8 w-full rounded-md bg-muted" />
        </div>
      </aside>

      {/* Mobile filter header skeleton - visible on mobile, hidden on lg+ */}
      <div className="lg:hidden w-full">
        <div className="rounded-lg border bg-card p-3 space-y-1">
          {/* Primary filter sections */}
          <div className="space-y-2">
            <TextSkeleton size="xs" width="sm" />
            <div className="h-10 w-full rounded-md bg-muted" />
          </div>
          <div className="space-y-2">
            <TextSkeleton size="xs" width="sm" />
            <div className="h-10 w-full rounded-md bg-muted" />
          </div>
          {/* SearchBar, which is h-14 */}
          <div className="h-14 w-full rounded-md bg-muted" />
          {/* Reset All, a size="sm" Button */}
          <div className="h-8 w-full rounded-md bg-muted" />
        </div>
      </div>

      {/* Main content area */}
      <div className="flex-1 min-w-0">
        {/* Card grid skeleton */}
        <div className={SECTION_STYLES.panel}>
          <div className="pt-4">
            <ResponsiveCardGrid size={size} mobileScale={mobileScale}>
              {Array.from({ length: cardCount }).map((_, i) => (
                <Skeleton
                  key={i}
                  style={{
                    width: widthPx,
                    height: heightPx,
                  }}
                />
              ))}
            </ResponsiveCardGrid>
          </div>
        </div>
      </div>
    </div>
  )
}

// ============================================================================
// Planner Grid Skeleton
// ============================================================================

interface PlannerGridSkeletonProps {
  /** The card's sizing, which the placeholder boxes take */
  geometry: CardGeometry
}

/**
 * PlannerGridSkeleton - Loading placeholder for planner card grid
 *
 * Simpler than ListPageSkeleton - just the card grid, no sidebar.
 * Used inside planner page content where toolbar/filters are already rendered.
 */
export function PlannerGridSkeleton({ geometry }: PlannerGridSkeletonProps) {
  const { size, mobileScale } = geometry
  const slotSize = useSlotSizePx(size, mobileScale)
  const { widthPx, heightPx } = slotSize
  const cardCount = useViewportFillCount(slotSize, CARD_GAP_PX)

  return (
    <ResponsiveCardGrid size={size}>
      {Array.from({ length: cardCount }).map((_, i) => (
        <Skeleton
          key={i}
          className="rounded-lg"
          style={{
            width: widthPx,
            height: heightPx,
          }}
        />
      ))}
    </ResponsiveCardGrid>
  )
}
