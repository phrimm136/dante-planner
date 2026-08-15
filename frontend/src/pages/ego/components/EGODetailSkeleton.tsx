import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * EGO detail: Header image + 2 panels (cost + resistance) (left)
 * Skill selector + skills + passives (right)
 */
export function EGODetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Header: image + name area */}
          <div className="flex gap-4">
            <Skeleton className="w-40 h-48 rounded-lg" /> {/* EGO portrait */}
            <div className="flex-1 space-y-2">
              <Skeleton className="h-6 w-16" /> {/* Type badge */}
              <Skeleton className="h-8 w-40" /> {/* Name */}
            </div>
          </div>
          {/* 2 panels: cost + resistance */}
          <div className="grid grid-cols-2 gap-2">
            <Skeleton className="h-28 rounded-lg" />
            <Skeleton className="h-28 rounded-lg" />
          </div>
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Skill type buttons */}
          <div className="flex gap-2">
            <Skeleton className="flex-1 h-10 rounded" />
            <Skeleton className="flex-1 h-10 rounded" />
          </div>
          {/* Skill cards */}
          <Skeleton className="h-40 rounded-lg" />
          <Skeleton className="h-36 rounded-lg" />
          {/* Passives panel */}
          <Skeleton className="h-32 rounded-lg" />
        </div>
      }
    />
  )
}
