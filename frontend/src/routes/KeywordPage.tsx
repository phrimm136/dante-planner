import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useKeywordListSpec } from '@/hooks/useKeywordListData'
import type { BuffType } from '@/lib/constants'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactBuffTypeFilter } from '@/components/filter/CompactBuffTypeFilter'
import { IdentitySearchDropdown } from '@/components/filter/IdentitySearchDropdown'
import { EGOSearchDropdown } from '@/components/filter/EGOSearchDropdown'
import { EgoGiftSearchDropdown } from '@/components/filter/EgoGiftSearchDropdown'
import { SearchBar } from '@/components/common/SearchBar'
import { KeywordList } from '@/components/keyword/KeywordList'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
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

  const [selectedBuffTypes, setSelectedBuffTypes] = useState<Set<BuffType>>(new Set())
  const [selectedIdentities, setSelectedIdentities] = useState<Set<string>>(new Set())
  const [selectedEgos, setSelectedEgos] = useState<Set<string>>(new Set())
  const [selectedEgoGifts, setSelectedEgoGifts] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  const handleResetAll = () => {
    setSelectedBuffTypes(new Set())
    setSelectedIdentities(new Set())
    setSelectedEgos(new Set())
    setSelectedEgoGifts(new Set())
    setSearchQuery('')
  }

  const activeFilterCount =
    selectedBuffTypes.size +
    selectedIdentities.size +
    selectedEgos.size +
    selectedEgoGifts.size

  const primaryFilters = (
    <>
      <FilterSection
        title={t('keyword.buffType')}
        activeCount={selectedBuffTypes.size}
      >
        <CompactBuffTypeFilter
          selectedBuffTypes={selectedBuffTypes}
          onBuffTypesChange={setSelectedBuffTypes}
        />
      </FilterSection>
    </>
  )

  const secondaryFilters = (
    <>
      <FilterSection
        title={t('keyword.filterIdentity')}
        activeCount={selectedIdentities.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <IdentitySearchDropdown
            selectedIdentities={selectedIdentities}
            onSelectionChange={setSelectedIdentities}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterEgo')}
        activeCount={selectedEgos.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EGOSearchDropdown
            selectedEgos={selectedEgos}
            onSelectionChange={setSelectedEgos}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterEgoGift')}
        activeCount={selectedEgoGifts.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EgoGiftSearchDropdown
            selectedEgoGifts={selectedEgoGifts}
            onSelectionChange={setSelectedEgoGifts}
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
        selectedBuffTypes={selectedBuffTypes}
        selectedIdentities={selectedIdentities}
        selectedEgos={selectedEgos}
        selectedEgoGifts={selectedEgoGifts}
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
