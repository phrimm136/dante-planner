import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { useEntityListData } from '@/hooks/useEntityListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftList } from '@/components/egoGift/EGOGiftList'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export default function EGOGiftPage() {
  const { t } = useTranslation()
  const { data: gifts, isPending, isError } = useEntityListData<EGOGiftListItem>('egoGift')
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  if (isPending) {
    return <LoadingState message="Loading EGO Gifts..." />
  }

  if (isError || !gifts) {
    return (
      <ErrorState
        title="Failed to Load EGO Gifts"
        message="Unable to load EGO Gift data. Please try again later."
      />
    )
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.egoGift.title', 'EGO Gifts')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.egoGift.description', 'Browse and search EGO Gifts')}
      </p>

      {/* Main content area */}
      <div className="bg-background rounded-lg p-6 space-y-4">
        {/* Top row: Keyword filter and sorter on left, Search bar on right */}
        <div className="flex gap-4 justify-between">
          {/* Left side: Filters and Sorter */}
          <div className="flex gap-4 items-center">
            <EGOGiftKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
            <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
          </div>

          {/* Right side: Search bar */}
          <div className="shrink-0">
            <EGOGiftSearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} />
          </div>
        </div>

        {/* Bottom: EGO Gift list */}
        <div>
          <EGOGiftList
            gifts={gifts}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
            sortMode={sortMode}
          />
        </div>
      </div>
    </div>
  )
}
