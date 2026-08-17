import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useKeywordListSpec } from '@/shared/gameText'
import type { BuffType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import type { KeywordFacetState } from './lib/keywordFilter'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { BuffTypeFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { IdentityFilterDropdown } from '@/pages/identity'
import { EGOFilterDropdown } from '@/pages/ego'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
import { KeywordList } from './components/KeywordList'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - builds keyword items from spec and passes to KeywordList.
 * The name list suspends into this section's own boundary, so the sidebar and
 * search bar stay on screen through a language change.
 *
 * Pattern Source: EGOGiftCardGrid in EGOGiftPage.tsx
 */
function KeywordCardGrid({
  spec,
  store,
}: {
  spec: Record<
    string,
    {
      iconId: string | null
      buffType: string
      identities: string[]
      egos: string[]
      egoGifts: string[]
    }
  >
  store: FilterStore<KeywordFacetState>
}) {
  const keywords = Object.entries(spec)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([id, entry]) => ({
      id,
      iconId: entry.iconId,
      buffType: entry.buffType,
      identities: entry.identities,
      egos: entry.egos,
      egoGifts: entry.egoGifts,
    }))

  return <KeywordList keywords={keywords} store={store} />
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

  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useSetFilters({
    selectedBuffTypes: new Set<BuffType>(),
    selectedIdentities: new Set<string>(),
    selectedEgos: new Set<string>(),
    selectedEgoGifts: new Set<string>(),
  })

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const primaryFilters = (
    <>
      <FilterSection title={t('keyword.buffType')} activeCount={filters.selectedBuffTypes.size}>
        <BuffTypeFilter
          selected={filters.selectedBuffTypes}
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
          <IdentityFilterDropdown
            selected={filters.selectedIdentities}
            onSelectionChange={setters.selectedIdentities}
            placeholderKey="keyword.filterIdentity"
          />
        </Suspense>
      </FilterSection>

      <FilterSection title={t('keyword.filterEgo')} activeCount={filters.selectedEgos.size}>
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EGOFilterDropdown
            selected={filters.selectedEgos}
            onSelectionChange={setters.selectedEgos}
            placeholderKey="keyword.filterEgo"
          />
        </Suspense>
      </FilterSection>

      <FilterSection title={t('keyword.filterEgoGift')} activeCount={filters.selectedEgoGifts.size}>
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <EGOGiftFilterDropdown
            selected={filters.selectedEgoGifts}
            onSelectionChange={setters.selectedEgoGifts}
          />
        </Suspense>
      </FilterSection>
    </>
  )

  return (
    <FilterPageLayout
      primaryFilters={primaryFilters}
      secondaryFilters={secondaryFilters}
      activeFilterCount={activeFilterCount}
      onResetAll={resetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('keyword.searchPlaceholder')}
        />
      }
    >
      <KeywordCardGrid spec={spec} store={store} />
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
    <EntityListPage skeleton={<ListPageSkeleton preset="keyword" filterCount={4} cardCount={30} />}>
      <KeywordPageShell />
    </EntityListPage>
  )
}
