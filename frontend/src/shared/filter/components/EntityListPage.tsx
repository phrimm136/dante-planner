import { Suspense } from 'react'
import { SECTION_STYLES } from '@/lib/constants'

interface EntityListPageProps {
  /** Fallback shown while the shell's spec query loads */
  skeleton: React.ReactNode
  /** Page shell holding the filter state and the list */
  children: React.ReactNode
}

/**
 * Outer chrome shared by the entity browser pages: page container plus the
 * Suspense boundary that covers initial spec loading.
 *
 * @example
 * <EntityListPage skeleton={<ListPageSkeleton preset="identity" />}>
 *   <IdentityPageShell />
 * </EntityListPage>
 */
export function EntityListPage({ skeleton, children }: EntityListPageProps) {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <Suspense fallback={skeleton}>{children}</Suspense>
    </div>
  )
}
