import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useEntityListData } from '@/hooks/useEntityListData'
import type { Identity } from '@/types/IdentityTypes'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { IdentityList } from '@/components/identity/IdentityList'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export default function IdentityPage() {
  const { t } = useTranslation()
  const { data: identities, isPending, isError } = useEntityListData<Identity>('identity')
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  if (isPending) {
    return <LoadingState message="Loading identities..." />
  }

  if (isError || !identities) {
    return (
      <ErrorState
        title="Failed to Load Identities"
        message="Unable to load identity data. Please try again later."
      />
    )
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.identity.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.identity.description')}
      </p>

      {/* Main content area matching mockup */}
      <div className="bg-background rounded-lg p-6 space-y-4">
        {/* Top row: Filters on left, Search bar on right with space between */}
        <div className="flex gap-4 justify-between">
          {/* Left side: Filters */}
          <div className="flex gap-4">
            <SinnerFilter
              selectedSinners={selectedSinners}
              onSelectionChange={setSelectedSinners}
            />
            <KeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>

          {/* Right side: Search bar */}
          <div className="shrink-0">
            <SearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} placeholder={t('pages.identity.searchBar')} />
          </div>
        </div>

        {/* Bottom: Identity list */}
        <div>
          <IdentityList
            identities={identities}
            selectedSinners={selectedSinners}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
          />
        </div>
      </div>
    </div>
  )
}
