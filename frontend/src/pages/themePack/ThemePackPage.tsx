import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/pages/themePack'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { CompactDungeonDifficultyFilter } from '@/shared/filter'
import { CompactFloorFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
import { ThemePackList } from '@/pages/themePack'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'

/**
 * Shell component - loads spec + i18n, manages filter states.
 * Does not suspend on language change since spec query key has no language.
 */
function ThemePackPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const { spec } = useThemePackListData()

  // Filter states
  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useSetFilters({
    selectedDifficulties: new Set<DungeonIdx>(),
    selectedFloors: new Set<ThemePackFloor>(),
    selectedEgoGifts: new Set<string>(),
  })

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedDifficulties',
      titleKey: 'filters.difficulty',
      titleFallback: 'Difficulty',
      Component: CompactDungeonDifficultyFilter,
      selected: filters.selectedDifficulties,
      onSelectionChange: setters.selectedDifficulties,
    }),
  ]

  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedFloors',
      titleKey: 'filters.floor',
      titleFallback: 'Floor',
      Component: CompactFloorFilter,
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
 * - ThemePackList: Uses theme pack i18n for name search
 */
export default function ThemePackPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton preset="themePack" />}>
      <ThemePackPageShell />
    </EntityListPage>
  )
}
