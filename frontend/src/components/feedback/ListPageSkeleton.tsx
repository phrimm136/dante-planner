import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { useSlotSizePx, type CardGeometry } from '@/shared/cardLayout'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

interface ListPageSkeletonProps {
  /** The card's sizing, which the placeholder boxes take */
  geometry: CardGeometry
  /** Number of skeleton cards */
  cardCount?: number
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
export function ListPageSkeleton({
  geometry,
  cardCount = 12,
  filterCount = 5,
}: ListPageSkeletonProps) {
  const { size, mobileScale } = geometry
  const { widthPx, heightPx } = useSlotSizePx(size, mobileScale)

  return (
    <div data-slot="page-skeleton" className="flex flex-col lg:flex-row gap-6">
      {/* Sidebar skeleton - hidden on mobile, visible on lg+ */}
      <aside className="hidden lg:block w-70 shrink-0">
        <div className="rounded-lg border bg-card p-3 space-y-2">
          {/* Filter section skeletons */}
          {Array.from({ length: filterCount }).map((_, i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-5 w-24" /> {/* Section title */}
              <Skeleton className="h-10 w-full rounded-md" /> {/* Filter content */}
            </div>
          ))}
          {/* Search bar inside sidebar */}
          <Skeleton className="h-10 w-full rounded-md" />
          {/* Reset button */}
          <Skeleton className="h-9 w-full rounded-md" />
        </div>
      </aside>

      {/* Mobile filter header skeleton - visible on mobile, hidden on lg+ */}
      <div className="lg:hidden w-full">
        <div className="rounded-lg border bg-card p-3 space-y-1">
          {/* Primary filter sections */}
          <div className="space-y-2">
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-10 w-full rounded-md" />
          </div>
          <div className="space-y-2">
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-10 w-full rounded-md" />
          </div>
          {/* Search bar */}
          <Skeleton className="h-10 w-full rounded-md" />
          {/* Reset button */}
          <Skeleton className="h-9 w-full rounded-md" />
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
  /** Number of skeleton cards */
  cardCount?: number
}

/**
 * PlannerGridSkeleton - Loading placeholder for planner card grid
 *
 * Simpler than ListPageSkeleton - just the card grid, no sidebar.
 * Used inside planner page content where toolbar/filters are already rendered.
 */
export function PlannerGridSkeleton({ geometry, cardCount = 8 }: PlannerGridSkeletonProps) {
  const { size, mobileScale } = geometry
  const { widthPx, heightPx } = useSlotSizePx(size, mobileScale)

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
