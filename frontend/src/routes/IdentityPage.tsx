import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import type { Identity } from '@/types/IdentityTypes'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { IdentityList } from '@/components/identity/IdentityList'

export default function IdentityPage() {
  const { t } = useTranslation()
  const { spec, i18n } = useIdentityListData()

  // Merge spec and i18n into Identity array
  const identities = useMemo<Identity[]>(() =>
    Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      rank: specData.rank,
      unitKeywordList: specData.unitKeywordList,
      skillKeywordList: specData.skillKeywordList,
    })),
    [spec, i18n]
  )

  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

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
