import { Suspense } from 'react'
import { SECTION_STYLES } from '@/lib/constants'

interface EntityListPageProps {
  skeleton: React.ReactNode
  children: React.ReactNode
}

export function EntityListPage({ skeleton, children }: EntityListPageProps) {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <Suspense fallback={skeleton}>{children}</Suspense>
    </div>
  )
}
