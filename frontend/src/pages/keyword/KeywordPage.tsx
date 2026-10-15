import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useKeywordListSpec } from '@/shared/gameText'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useFilterStore } from '@/components/hooks/filterStore'
import type { FilterStore } from '@/components/hooks/filterStore'
import { keywordFilterStore } from './stores/keywordFilterStore'
import type { KeywordFacetState } from './lib/keywordFilter'
import { toKeywordEntity } from './lib/keywordEntity'
import type { BattleKeywordSpecList } from './types/KeywordTypes'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { BuffTypeFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { IdentityFilterDropdown } from '@/pages/identity'
import { EGOFilterDropdown } from '@/pages/ego'
import { EGOGiftFilterDropdown } from '@/shared/egoGift'
import { BattleFilterDropdown } from '@/pages/battle'
import { KeywordList } from './components/KeywordList'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { KEYWORD_GEOMETRY } from './lib/cardLayout'
import { Skeleton } from '@/components/ui/skeleton'

function KeywordCardGrid({
  spec,
  store,
}: {
  spec: BattleKeywordSpecList
  store: FilterStore<KeywordFacetState>
}) {
  const keywords = Object.entries(spec)
    .map(([id, entry]) => toKeywordEntity(id, entry))
    .sort((a, b) => a.id.localeCompare(b.id))

  return <KeywordList keywords={keywords} store={store} />
}

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
  } = useFilterStore(keywordFilterStore)

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

      <FilterSection title={t('keyword.filterEnemy')} activeCount={filters.selectedEnemies.size}>
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleFilterDropdown
            family="enemy"
            selected={filters.selectedEnemies}
            onSelectionChange={setters.selectedEnemies}
            placeholderKey="keyword.filterEnemy"
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterAssistant')}
        activeCount={filters.selectedAssistants.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleFilterDropdown
            family="assistant"
            selected={filters.selectedAssistants}
            onSelectionChange={setters.selectedAssistants}
            placeholderKey="keyword.filterAssistant"
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('keyword.filterSinnerEvent')}
        activeCount={filters.selectedSinnerEvents.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleFilterDropdown
            family="sinnerEvent"
            selected={filters.selectedSinnerEvents}
            onSelectionChange={setters.selectedSinnerEvents}
            placeholderKey="keyword.filterSinnerEvent"
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

export default function KeywordPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton geometry={KEYWORD_GEOMETRY} filterCount={7} />}>
      <KeywordPageShell />
    </EntityListPage>
  )
}
