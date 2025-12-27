import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftList } from '@/components/egoGift/EGOGiftList'

export default function EGOGiftPage() {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOGiftListData()

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() =>
    Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
    })),
    [spec, i18n]
  )

  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

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
