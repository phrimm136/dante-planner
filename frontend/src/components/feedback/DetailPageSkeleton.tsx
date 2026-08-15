import type { ReactNode } from 'react'

import { DETAIL_PAGE } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface DetailPageSkeletonProps {
  /** Left column content */
  left: ReactNode
  /** Right column content */
  right: ReactNode
}

/**
 * DetailPageSkeleton - Loading placeholder shell for detail pages
 *
 * Matches DetailPageLayout structure:
 * - Desktop: 4:6 ratio two-column grid (10-column grid)
 * - Mobile: Single column layout
 */
export function DetailPageSkeleton({ left, right }: DetailPageSkeletonProps) {
  return (
    <div className="container mx-auto p-4 sm:p-6 lg:p-8">
      <div className="grid grid-cols-10 gap-6">
        {/* Left column: 4/10 on desktop, full width on mobile */}
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_LEFT)}>{left}</div>
        {/* Right column: 6/10 on desktop, full width on mobile */}
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_RIGHT)}>{right}</div>
      </div>
    </div>
  )
}
