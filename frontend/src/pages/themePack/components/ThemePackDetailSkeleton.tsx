import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { CardSlot } from '@/shared/cardLayout'
import { THEME_PACK_GEOMETRY } from '../lib/cardLayout'

/**
 * Theme Pack detail: Card image + difficulty/floor metadata (left)
 * Specific gifts + events sections (right)
 */
export function ThemePackDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="flex gap-4">
          {/* Theme pack card image */}
          <CardSlot size={THEME_PACK_GEOMETRY.size} className="shrink-0">
            <Skeleton className="size-full rounded-lg" />
          </CardSlot>
          {/* Metadata panel: difficulty + floors */}
          <div className="flex-1 space-y-3">
            <Skeleton className="h-5 w-20" />
            <div className="flex gap-2">
              <Skeleton className="h-6 w-16 rounded" />
              <Skeleton className="h-6 w-16 rounded" />
            </div>
            <Skeleton className="h-5 w-16" />
            <div className="flex gap-1">
              <Skeleton className="h-6 w-10 rounded" />
              <Skeleton className="h-6 w-10 rounded" />
              <Skeleton className="h-6 w-10 rounded" />
            </div>
          </div>
        </div>
      }
      right={
        <div className="space-y-6">
          {/* Section: Specific gifts */}
          <div className="space-y-3">
            <Skeleton className="h-5 w-48" />
            <div className="flex gap-3">
              <Skeleton className="w-24 h-24 rounded" />
              <Skeleton className="w-24 h-24 rounded" />
              <Skeleton className="w-24 h-24 rounded" />
            </div>
          </div>
          {/* Section: Exclusive events */}
          <div className="space-y-3">
            <Skeleton className="h-5 w-48" />
            <div className="flex gap-3">
              <Skeleton className="w-40 h-28 rounded" />
              <Skeleton className="w-40 h-28 rounded" />
            </div>
          </div>
          {/* Section: All gifts */}
          <div className="space-y-3">
            <Skeleton className="h-5 w-40" />
            <div className={SECTION_STYLES.LAYOUT.wrap}>
              {Array.from({ length: 12 }).map((_, i) => (
                <Skeleton key={i} className="w-24 h-24 rounded" />
              ))}
            </div>
          </div>
          {/* Section: All events */}
          <div className="space-y-3">
            <Skeleton className="h-5 w-48" />
            <div className="flex flex-wrap gap-3">
              <Skeleton className="w-40 h-28 rounded" />
              <Skeleton className="w-40 h-28 rounded" />
              <Skeleton className="w-40 h-28 rounded" />
            </div>
          </div>
        </div>
      }
    />
  )
}
