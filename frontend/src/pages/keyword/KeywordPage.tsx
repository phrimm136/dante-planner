import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useKeywordListSpec } from '@/shared/gameText'
import type { BuffType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { CompactBuffTypeFilter } from '@/shared/filter'
import { IdentitySearchDropdown } from '@/shared/filter'
import { EGOSearchDropdown } from '@/shared/filter'
import { EgoGiftSearchDropdown } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { useIdentityListData } from '@/pages/identity'
import { useEGOListData } from '@/pages/ego'
import { useEGOGiftListData } from '@/pages/egoGift'
import { KeywordList } from './components/KeywordList'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - builds keyword items from spec and passes to KeywordList.
 * No suspension at grid level (name search uses deferred hook in KeywordList).
 *
 * Pattern Source: EGOGiftCardGrid in EGOGiftPage.tsx
 */
function KeywordCardGrid({
  spec,
  selectedBuffTypes,
  selectedIdentities,
  selectedEgos,
  selectedEgoGifts,
  searchQuery,
}: {
  spec: Record<string, { iconId: string | null; buffType: string; identities: string[]; egos: string[]; egoGifts: string[] }>
  selectedBuffTypes: Set<BuffType>
  selectedIdentities: Set<string>
  selectedEgos: Set<string>
  selectedEgoGifts: Set<string>
  searchQuery: string
}) {
  const keywords = useMemo(
    () =>
      Object.entries(spec)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([id, entry]) => ({
          id,
          iconId: entry.iconId,
          buffType: entry.buffType,
          identities: entry.identities,
          egos: entry.egos,
          egoGifts: entry.egoGifts,
        })),
    [spec]
  )

  return (
    <KeywordList
      keywords={keywords}
      selectedBuffTypes={selectedBuffTypes}
      selectedIdentities={selectedIdentities}
      selectedEgos={selectedEgos}
      selectedEgoGifts={selectedEgoGifts}
      searchQuery={searchQuery}
    />
  )
}

/**
 * Shell component - uses spec data only (no language dependency).
 * Does not suspend on language change since spec query key has no language.
 *
 * Pattern Source: EGOGiftPageShell in EGOGiftPage.tsx
 */
function KeywordPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useKeywordListSpec()

  const { values: filters, setters, resetAll } = useSetFilters({
    selectedBuffTypes: new Set<BuffType>(),
    selectedIdentities: new Set<string>(),
    selectedEgos: new Set<string>(),
    selectedEgoGifts: new Set<string>(),
  })
  const [searchQuery, setSearchQuery] = useState<string>('')

  const handleResetAll = () => {
    resetAll()
    setSearchQuery('')
  }

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const primaryFilters = (
    <>
      <FilterSection
        title={t('keyword.buffType')}
        activeCount={filters.selectedBuffTypes.size}
      >
        <CompactBuffTypeFilter
          selectedBuffTypes={filters.selectedBuffTypes}
          onBuffTypesChange={setters.selectedBuffTypes}
        />
      </FilterSection>
    </>
  )

  const secondaryFilters = (
    <>
      <FilterSection
        title={t('keyword.filterIdentity')}
        activeCount={filters.selectedIdentities.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <IdentitySearchDropdown
            selectedIdentities={filters.selectedIdentities}
            onSelectionChange={setters.selectedIdentities}
            useListData={useIdentityListData}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterEgo')}
        activeCount={filters.selectedEgos.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EGOSearchDropdown
            selectedEgos={filters.selectedEgos}
            onSelectionChange={setters.selectedEgos}
            useListData={useEGOListData}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterEgoGift')}
        activeCount={filters.selectedEgoGifts.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EgoGiftSearchDropdown
            selectedEgoGifts={filters.selectedEgoGifts}
            onSelectionChange={setters.selectedEgoGifts}
            useListData={useEGOGiftListData}
          />
        </Suspense>
      </FilterSection>
    </>
  )

  const filterContent = (
    <>
      {primaryFilters}
      {secondaryFilters}
    </>
  )

  return (
    <FilterPageLayout
      filterContent={filterContent}
      primaryFilters={primaryFilters}
      secondaryFilters={secondaryFilters}
      activeFilterCount={activeFilterCount}
      onResetAll={handleResetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('keyword.searchPlaceholder')}
        />
      }
    >
      <KeywordCardGrid
        spec={spec}
        selectedBuffTypes={filters.selectedBuffTypes}
        selectedIdentities={filters.selectedIdentities}
        selectedEgos={filters.selectedEgos}
        selectedEgoGifts={filters.selectedEgoGifts}
        searchQuery={searchQuery}
      />
    </FilterPageLayout>
  )
}

/**
 * KeywordPage - Keyword browser with responsive filter sidebar
 *
 * Pattern Source: EGOGiftPage.tsx
 */
export default function KeywordPage() {
  return (
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="keyword" filterCount={4} cardCount={30} />}>
        <KeywordPageShell />
      </Suspense>
    </div>
  )
}
