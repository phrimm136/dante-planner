import { Skeleton } from '@/components/ui/skeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { CARD_GAP_PX, SECTION_STYLES } from '@/lib/constants'
import { useSlotSizePx, type CardGeometry } from '@/shared/cardLayout'
import { useViewportFillCount } from '@/components/hooks/useViewportFillCount'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

interface ListPageSkeletonProps {
  geometry: CardGeometry
  filterCount?: number
}

export function ListPageSkeleton({ geometry, filterCount = 5 }: ListPageSkeletonProps) {
  const { size, mobileScale } = geometry
  const slotSize = useSlotSizePx(size, mobileScale)
  const { widthPx, heightPx } = slotSize
  const cardCount = useViewportFillCount(slotSize, CARD_GAP_PX)

  return (
    <div data-slot="page-skeleton" className="flex flex-col lg:flex-row gap-6">
      <aside className="hidden lg:block w-70 shrink-0">
        <div className="rounded-lg border bg-card p-3 space-y-2">
          {Array.from({ length: filterCount }).map((_, i) => (
            <div key={i} className="space-y-2">
              <TextSkeleton size="xs" width="sm" />
              <div className="h-10 w-full rounded-md bg-muted" />
            </div>
          ))}
          <div className="h-14 w-full rounded-md bg-muted" />
          <div className="h-8 w-full rounded-md bg-muted" />
        </div>
      </aside>

      <div className="lg:hidden w-full">
        <div className="rounded-lg border bg-card p-3 space-y-1">
          <div className="space-y-2">
            <TextSkeleton size="xs" width="sm" />
            <div className="h-10 w-full rounded-md bg-muted" />
          </div>
          <div className="space-y-2">
            <TextSkeleton size="xs" width="sm" />
            <div className="h-10 w-full rounded-md bg-muted" />
          </div>
          <div className="h-14 w-full rounded-md bg-muted" />
          <div className="h-8 w-full rounded-md bg-muted" />
        </div>
      </div>

      <div className="flex-1 min-w-0">
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

interface PlannerGridSkeletonProps {
  geometry: CardGeometry
}

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
