import { Suspense } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { FormattedKeyword } from './FormattedKeyword'
import { useKeywordFormatter } from '../hooks/useKeywordFormatter'

interface KeywordStringProps {
  /** Bare keyword key, e.g. "Sinking" */
  keyword: string
  /** Additional CSS classes forwarded to the rendered keyword */
  className?: string | undefined
}

/**
 * Self-suspending clickable keyword chip: resolves a bare key to icon +
 * colored text (with popover) and shows an inline skeleton while the
 * keyword i18n data loads.
 */
export function KeywordString({ keyword, className }: KeywordStringProps) {
  return (
    <Suspense fallback={<Skeleton className="inline-block h-4 w-16 align-middle" />}>
      <ResolvedKeywordString keyword={keyword} className={className} />
    </Suspense>
  )
}

/** Inner half: the actual suspending hook consumer */
function ResolvedKeywordString({ keyword, className }: KeywordStringProps) {
  const { resolve } = useKeywordFormatter()
  return <FormattedKeyword keyword={resolve(keyword)} className={className} />
}
