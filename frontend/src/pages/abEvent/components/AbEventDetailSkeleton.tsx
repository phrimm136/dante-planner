import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Ab Event detail: Event image + related gifts/packs (left)
 * Choice branches with effects (right)
 */
export function AbEventDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Event image (wide landscape) */}
          <Skeleton className="w-full aspect-[3/2] rounded-lg" />
          {/* Related EGO gifts */}
          <div className="space-y-2">
            <Skeleton className="h-5 w-32" />
            <div className="flex gap-2">
              <Skeleton className="w-24 h-24 rounded" />
              <Skeleton className="w-24 h-24 rounded" />
            </div>
          </div>
          {/* Related theme packs */}
          <div className="space-y-2">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-4 w-36" />
          </div>
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Event description */}
          <Skeleton className="h-24 rounded-lg" />
          {/* Choice branches */}
          <Skeleton className="h-32 rounded-lg" />
          <Skeleton className="h-40 rounded-lg" />
          <Skeleton className="h-32 rounded-lg" />
        </div>
      }
    />
  )
}
