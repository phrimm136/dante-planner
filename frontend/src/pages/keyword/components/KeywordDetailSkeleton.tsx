import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Keyword detail: Icon + name + backlinks panel (left)
 * Description panel (right)
 */
export function KeywordDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Header: icon + name */}
          <div className="flex gap-4 items-center">
            <Skeleton className="w-24 h-24 rounded-lg" />
            <Skeleton className="h-8 w-32" />
          </div>
          {/* Backlinks panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="space-y-1.5">
              <Skeleton className="h-4 w-28" />
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-4 w-40" />
            </div>
            <div className="space-y-1.5">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 w-44" />
            </div>
            <div className="space-y-1.5">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-4 w-36" />
            </div>
          </div>
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Description panel */}
          <div className="border rounded p-4 space-y-3">
            <Skeleton className="h-6 w-28" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        </div>
      }
    />
  )
}
