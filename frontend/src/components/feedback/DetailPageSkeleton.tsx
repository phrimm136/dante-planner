import type { ReactNode } from 'react'

import { DETAIL_PAGE } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface DetailPageSkeletonProps {
  left: ReactNode
  right: ReactNode
}

export function DetailPageSkeleton({ left, right }: DetailPageSkeletonProps) {
  return (
    <div data-slot="page-skeleton" className="container mx-auto p-4 sm:p-6 lg:p-8">
      <div className="grid grid-cols-10 gap-6">
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_LEFT)}>{left}</div>
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_RIGHT)}>{right}</div>
      </div>
    </div>
  )
}
