import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * EGO Gift detail: Card + name + metadata (left)
 * Enhancement descriptions panel (right)
 */
export function EGOGiftDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Header: card + name */}
          <div className="flex gap-4 items-center">
            <Skeleton className="w-24 h-24 rounded-lg" /> {/* Gift card */}
            <Skeleton className="h-8 w-32" /> {/* Name */}
          </div>
          {/* Metadata panel */}
          <Skeleton className="h-24 rounded-lg" />
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Enhancement panels */}
          <Skeleton className="h-20 rounded-lg" />
          <Skeleton className="h-20 rounded-lg" />
          <Skeleton className="h-20 rounded-lg" />
        </div>
      }
    />
  )
}
