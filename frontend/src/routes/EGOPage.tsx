import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useEntityListData } from '@/hooks/useEntityListData'
import type { EGO } from '@/types/EGOTypes'
import { EGOSinnerFilter } from '@/components/ego/EGOSinnerFilter'
import { EGOKeywordFilter } from '@/components/ego/EGOKeywordFilter'
import { EGOSearchBar } from '@/components/ego/EGOSearchBar'
import { EGOList } from '@/components/ego/EGOList'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export default function EGOPage() {
  const { t } = useTranslation()
  const { data: egos, isPending, isError } = useEntityListData<EGO>('ego')
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  if (isPending) {
    return <LoadingState message="Loading EGOs..." />
  }

  if (isError || !egos) {
    return (
      <ErrorState
        title="Failed to Load EGOs"
        message="Unable to load EGO data. Please try again later."
      />
    )
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.ego.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.ego.description')}
      </p>

      {/* Main content area matching identity page */}
      <div className="bg-background rounded-lg p-6 space-y-4">
        {/* Top row: Filters on left, Search bar on right with space between */}
        <div className="flex gap-4 justify-between">
          {/* Left side: Filters */}
          <div className="flex gap-4">
            <EGOSinnerFilter
              selectedSinners={selectedSinners}
              onSelectionChange={setSelectedSinners}
            />
            <EGOKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>

          {/* Right side: Search bar */}
          <div className="shrink-0">
            <EGOSearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} />
          </div>
        </div>

        {/* Bottom: EGO list */}
        <div>
          <EGOList
            egos={egos}
            selectedSinners={selectedSinners}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
          />
        </div>
      </div>
    </div>
  )
}
