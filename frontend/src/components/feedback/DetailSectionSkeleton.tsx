import type { ReactNode } from 'react'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'

interface DetailSectionSkeletonProps {
  children: ReactNode
}

export function DetailSectionSkeleton({ children }: DetailSectionSkeletonProps) {
  return (
    <div className={SECTION_STYLES.SPACING.content}>
      <TextSkeleton size="lg" width="xs" />
      {children}
    </div>
  )
}
