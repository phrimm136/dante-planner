import { Skeleton } from '@/components/ui/skeleton'
import { CARD_GRID } from '@/lib/constants'
import { ResponsiveCardGrid } from './ResponsiveCardGrid'

/**
 * Card dimension presets matching actual card components
 *
 * clipPath matches IdentityCard's octagonal shape:
 * polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)
 */
const CARD_PRESETS = {
  /** IdentityCard: w-40 (160px) × h-56 (224px), octagonal clipPath */
  identity: {
    width: CARD_GRID.WIDTH.IDENTITY,
    height: CARD_GRID.HEIGHT.IDENTITY,
    clipPath: 'polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)',
    mobileScale: 0.8,
  },
  /** EGOCard: w-40 (160px) × h-48 (192px), circular center image */
  ego: {
    width: CARD_GRID.WIDTH.EGO,
    height: CARD_GRID.HEIGHT.EGO,
    clipPath: 'circle(45%)',
    mobileScale: 0.8,
  },
  /** EGOGiftCard: w-24 (96px) × h-24 (96px), square */
  egoGift: {
    width: CARD_GRID.WIDTH.EGO_GIFT,
    height: 96,
    clipPath: undefined,
    mobileScale: 0.8,
  },
  /** PlannerCard: 280px × 160px, rounded rectangle */
  planner: {
    width: CARD_GRID.WIDTH.PLANNER,
    height: 160,
    clipPath: undefined,
    mobileScale: 1,
  },
} as const

type CardPreset = keyof typeof CARD_PRESETS

interface ListPageSkeletonProps {
  /** Card preset for grid skeleton */
  preset?: CardPreset
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
  preset = 'identity',
  cardCount = 12,
  filterCount = 5,
}: ListPageSkeletonProps) {
  const { width: cardWidth, height: cardHeight, clipPath, mobileScale } = CARD_PRESETS[preset]

  return (
    <div className="flex flex-col lg:flex-row gap-6">
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
        <div className="bg-muted border border-border rounded-md p-6">
          <div className="pt-4">
            <ResponsiveCardGrid
              cardWidth={cardWidth}
              cardHeight={cardHeight}
              mobileScale={mobileScale}
            >
              {Array.from({ length: cardCount }).map((_, i) => (
                <Skeleton
                  key={i}
                  style={{
                    width: cardWidth,
                    height: cardHeight,
                    clipPath,
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
  /** Number of skeleton cards */
  cardCount?: number
}

/**
 * PlannerGridSkeleton - Loading placeholder for planner card grid
 *
 * Simpler than ListPageSkeleton - just the card grid, no sidebar.
 * Used inside planner page content where toolbar/filters are already rendered.
 */
export function PlannerGridSkeleton({ cardCount = 8 }: PlannerGridSkeletonProps) {
  const { width: cardWidth, height: cardHeight } = CARD_PRESETS.planner

  return (
    <ResponsiveCardGrid cardWidth={cardWidth} cardHeight={cardHeight}>
      {Array.from({ length: cardCount }).map((_, i) => (
        <Skeleton
          key={i}
          className="rounded-lg"
          style={{
            width: cardWidth,
            height: cardHeight,
          }}
        />
      ))}
    </ResponsiveCardGrid>
  )
}
