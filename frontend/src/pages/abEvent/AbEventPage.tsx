import { useTranslation } from 'react-i18next'
import { ThemePackFilterDropdown } from '@/pages/themePack'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useFilterStore } from '@/components/hooks/filterStore'
import { abEventFilterStore } from './stores/abEventFilterStore'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
import { AbEventList, useAbEventListSpec } from '@/pages/abEvent'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { AB_EVENT_GEOMETRY } from './lib/cardLayout'

function AbEventPageShell() {
  const { t } = useTranslation('database')
  const spec = useAbEventListSpec()

  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(abEventFilterStore)

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedEgoGifts',
      titleKey: 'filters.egoGift',
      titleFallback: 'EGO Gift',
      suspense: true,
      Component: EGOGiftFilterDropdown,
      selected: filters.selectedEgoGifts,
      onSelectionChange: setters.selectedEgoGifts,
    }),
  ]

  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedThemePacks',
      titleKey: 'filters.themePack',
      titleFallback: 'Theme Pack',
      suspense: true,
      Component: ThemePackFilterDropdown,
      selected: filters.selectedThemePacks,
      onSelectionChange: setters.selectedThemePacks,
    }),
  ]

  return (
    <FilterPageLayout
      primaryFilters={<FilterSectionList sections={PRIMARY_FILTERS} />}
      secondaryFilters={<FilterSectionList sections={SECONDARY_FILTERS} />}
      activeFilterCount={activeFilterCount}
      onResetAll={resetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('pages.abEvent.searchBar', 'Search Events...')}
        />
      }
    >
      <AbEventList spec={spec} store={store} />
    </FilterPageLayout>
  )
}

export default function AbEventPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton geometry={AB_EVENT_GEOMETRY} />}>
      <AbEventPageShell />
    </EntityListPage>
  )
}
