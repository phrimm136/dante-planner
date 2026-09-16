import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { useSlotSizePx } from '@/shared/cardLayout'
import { getEGOMaskPath } from '@/shared/assets'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { EGO_GEOMETRY, egoPortraitWindowStyle } from '../lib/cardLayout'

interface EGOListSkeletonProps {
  /** Number of skeleton cards */
  cardCount?: number
  /** Number of filter section skeletons in sidebar */
  filterCount?: number
}

/**
 * Loading placeholder for the EGO browser.
 *
 * The placeholder is the portrait window itself, stencilled by the same mask sprite the
 * card draws, so the pulsing shape is the shape the card resolves to.
 */
export function EGOListSkeleton({ cardCount = 12, filterCount = 5 }: EGOListSkeletonProps) {
  const { size, mobileScale } = EGO_GEOMETRY
  const { widthPx, heightPx } = useSlotSizePx(size, mobileScale)
  const maskPath = getEGOMaskPath()

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
                <div key={i} className="relative" style={{ width: widthPx, height: heightPx }}>
                  <Skeleton style={egoPortraitWindowStyle(maskPath)} />
                </div>
              ))}
            </ResponsiveCardGrid>
          </div>
        </div>
      </div>
    </div>
  )
}
