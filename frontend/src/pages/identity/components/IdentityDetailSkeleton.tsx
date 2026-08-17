import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Identity detail: Header image + 3 stat panels + traits (left)
 * Skills + Passives + Sanity panels (right)
 */
export function IdentityDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Header: image + name area */}
          <div className="flex gap-4">
            <Skeleton className="w-40 h-56 rounded-lg" /> {/* Portrait */}
            <div className="flex-1 space-y-2">
              <Skeleton className="h-6 w-20" /> {/* Rank badge */}
              <Skeleton className="h-8 w-48" /> {/* Name */}
            </div>
          </div>
          {/* 3 stat panels */}
          <div className="grid grid-cols-3 gap-2">
            <Skeleton className="h-24 rounded-lg" />
            <Skeleton className="h-24 rounded-lg" />
            <Skeleton className="h-24 rounded-lg" />
          </div>
          {/* Traits */}
          <Skeleton className="h-12 rounded-lg" />
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Selector */}
          <Skeleton className="h-12 rounded-lg" />
          {/* Skill buttons */}
          <div className="flex gap-2">
            <Skeleton className="flex-1 h-10 rounded" />
            <Skeleton className="flex-1 h-10 rounded" />
            <Skeleton className="flex-1 h-10 rounded" />
            <Skeleton className="flex-1 h-10 rounded" />
          </div>
          {/* Skill card */}
          <Skeleton className="h-48 rounded-lg" />
          {/* Passives panel */}
          <Skeleton className="h-32 rounded-lg" />
          {/* Sanity panel */}
          <Skeleton className="h-40 rounded-lg" />
        </div>
      }
    />
  )
}
