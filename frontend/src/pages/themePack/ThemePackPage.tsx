import { useTranslation } from 'react-i18next'
import { useThemePackListSpec } from '@/pages/themePack'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useFilterStore } from '@/components/hooks/filterStore'
import { themePackFilterStore } from './stores/themePackFilterStore'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { DungeonDifficultyFilter } from '@/shared/filter'
import { FloorFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
import { ThemePackList } from '@/pages/themePack'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { THEME_PACK_GEOMETRY } from './lib/cardLayout'

/**
 * Shell component - loads spec, manages filter states.
 * Does not suspend on language change since spec query key has no language.
 */
function ThemePackPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useThemePackListSpec()

  // Filter states
  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(themePackFilterStore)

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedDifficulties',
      titleKey: 'filters.difficulty',
      titleFallback: 'Difficulty',
      Component: DungeonDifficultyFilter,
      selected: filters.selectedDifficulties,
      onSelectionChange: setters.selectedDifficulties,
    }),
  ]

  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedFloors',
      titleKey: 'filters.floor',
      titleFallback: 'Floor',
      Component: FloorFilter,
      selected: filters.selectedFloors,
      onSelectionChange: setters.selectedFloors,
    }),
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
          placeholder={t('pages.themePack.searchBar', 'Search Theme Packs...')}
        />
      }
    >
      <ThemePackList spec={spec} store={store} />
    </FilterPageLayout>
  )
}

/**
 * ThemePackPage - Theme pack browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - EGO Gift dropdown: Own Suspense for i18n
 * - ThemePackList: names suspend at the card, search terms never suspend
 */
export default function ThemePackPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton geometry={THEME_PACK_GEOMETRY} />}>
      <ThemePackPageShell />
    </EntityListPage>
  )
}
