import { Suspense } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { FormattedKeyword } from './FormattedKeyword'
import { useKeywordFormatter } from '../hooks/useKeywordFormatter'

interface KeywordStringProps {
  keyword: string
  className?: string | undefined
}

export function KeywordString({ keyword, className }: KeywordStringProps) {
  return (
    <Suspense fallback={<Skeleton className="inline-block h-4 w-16 align-middle" />}>
      <ResolvedKeywordString keyword={keyword} className={className} />
    </Suspense>
  )
}

function ResolvedKeywordString({ keyword, className }: KeywordStringProps) {
  const { resolve } = useKeywordFormatter()
  return <FormattedKeyword keyword={resolve(keyword)} className={className} />
}
