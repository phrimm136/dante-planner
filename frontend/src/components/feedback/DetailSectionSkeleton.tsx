import type { ReactNode } from 'react'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { Separator } from '@/components/ui/separator'
import { SECTION_STYLES } from '@/lib/constants'

interface DetailSectionSkeletonProps {
  children: ReactNode
}

export function DetailSectionSkeleton({ children }: DetailSectionSkeletonProps) {
  return (
    <div className={SECTION_STYLES.SPACING.content}>
      <TextSkeleton size="lg" width="xs" />
      <Separator />
      {children}
    </div>
  )
}
