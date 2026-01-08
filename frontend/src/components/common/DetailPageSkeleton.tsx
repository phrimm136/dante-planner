import { Skeleton } from '@/components/ui/skeleton'
import { DETAIL_PAGE } from '@/lib/constants'
import { cn } from '@/lib/utils'

/**
 * Preset configurations for different detail page types
 * Each preset defines the skeleton shapes for left and right columns
 */
const DETAIL_PRESETS = {
  /**
   * Identity detail: Header image + 3 stat panels + traits (left)
   * Skills + Passives + Sanity panels (right)
   */
  identity: {
    left: (
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
    ),
    right: (
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
    ),
  },

  /**
   * EGO detail: Header image + 2 panels (cost + resistance) (left)
   * Skill selector + skills + passives (right)
   */
  ego: {
    left: (
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
    ),
    right: (
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
    ),
  },

  /**
   * EGO Gift detail: Card + name + metadata (left)
   * Enhancement descriptions panel (right)
   */
  egoGift: {
    left: (
      <div className="space-y-4">
        {/* Header: card + name */}
        <div className="flex gap-4 items-center">
          <Skeleton className="w-24 h-24 rounded-lg" /> {/* Gift card */}
          <Skeleton className="h-8 w-32" /> {/* Name */}
        </div>
        {/* Metadata panel */}
        <Skeleton className="h-24 rounded-lg" />
      </div>
    ),
    right: (
      <div className="space-y-4">
        {/* Enhancement panels */}
        <Skeleton className="h-20 rounded-lg" />
        <Skeleton className="h-20 rounded-lg" />
        <Skeleton className="h-20 rounded-lg" />
      </div>
    ),
  },
} as const

type DetailPreset = keyof typeof DETAIL_PRESETS

interface DetailPageSkeletonProps {
  /** Entity type preset */
  preset: DetailPreset
}

/**
 * DetailPageSkeleton - Loading placeholder for detail pages
 *
 * Matches DetailPageLayout structure:
 * - Desktop: 4:6 ratio two-column grid (10-column grid)
 * - Mobile: Single column layout
 *
 * Shows pulsing skeleton matching the actual content shape.
 */
export function DetailPageSkeleton({ preset }: DetailPageSkeletonProps) {
  const { left, right } = DETAIL_PRESETS[preset]

  return (
    <div className="container mx-auto p-4 sm:p-6 lg:p-8">
      <div className="grid grid-cols-10 gap-6">
        {/* Left column: 4/10 on desktop, full width on mobile */}
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_LEFT)}>
          {left}
        </div>
        {/* Right column: 6/10 on desktop, full width on mobile */}
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_RIGHT)}>
          {right}
        </div>
      </div>
    </div>
  )
}
