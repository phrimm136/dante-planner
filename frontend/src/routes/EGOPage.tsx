import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListData } from '@/hooks/useEGOListData'
import type { EGO } from '@/types/EGOTypes'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOList } from '@/components/ego/EGOList'

export default function EGOPage() {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOListData()

  // Merge spec and i18n into EGO array
  const egos = useMemo<EGO[]>(() =>
    Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      rank: specData.egoType,
      attributeType: specData.attributeType,
      skillKeywordList: specData.skillKeywordList,
    })),
    [spec, i18n]
  )

  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

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
            <SearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} placeholder={t('pages.ego.searchBar')} />
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
