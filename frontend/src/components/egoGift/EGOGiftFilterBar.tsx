import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'

interface EGOGiftFilterBarProps {
  selectedKeywords: Set<string>
  onKeywordsChange: (keywords: Set<string>) => void
  sortMode: SortMode
  onSortModeChange: (mode: SortMode) => void
  searchQuery: string
  onSearchChange: (query: string) => void
  className?: string
}

/**
 * Reusable filter bar for EGO gift selection contexts.
 * Row 1: Keyword filter (full width)
 * Row 2: Sorter + SearchBar
 */
export function EGOGiftFilterBar({
  selectedKeywords,
  onKeywordsChange,
  sortMode,
  onSortModeChange,
  searchQuery,
  onSearchChange,
  className,
}: EGOGiftFilterBarProps) {
  const { t } = useTranslation('planner')

  return (
    <div className={cn('space-y-2 lg:space-y-0 lg:flex lg:items-center lg:gap-4', className)}>
      <div className="shrink-0">
        <EGOGiftKeywordFilter
          selectedKeywords={selectedKeywords}
          onSelectionChange={onKeywordsChange}
        />
      </div>
      <div className="flex flex-col sm:flex-row gap-2 sm:gap-4 lg:flex-1 lg:min-w-0">
        <div className="shrink-0">
          <Sorter sortMode={sortMode} onSortModeChange={onSortModeChange} />
        </div>
        <div className="flex-1 min-w-0">
          <SearchBar
            searchQuery={searchQuery}
            onSearchChange={onSearchChange}
            placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
          />
        </div>
      </div>
    </div>
  )
}
