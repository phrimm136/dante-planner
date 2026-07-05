import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/pages/themePack'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { CompactDungeonDifficultyFilter } from '@/shared/filter'
import { CompactFloorFilter } from '@/shared/filter'
import { EgoGiftSearchDropdown } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { useEGOGiftListData } from '@/pages/egoGift'
import { ThemePackList } from '@/pages/themePack'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

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
    resetAll,
  } = useSetFilters({
    selectedDifficulties: new Set<DungeonIdx>(),
    selectedFloors: new Set<ThemePackFloor>(),
    selectedEgoGifts: new Set<string>(),
  })
  const [searchQuery, setSearchQuery] = useState<string>('')

  const handleResetAll = () => {
    resetAll()
    setSearchQuery('')
  }

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const primaryFilters = (
    <FilterSection
      title={t('filters.difficulty', 'Difficulty')}
      activeCount={filters.selectedDifficulties.size}
    >
      <CompactDungeonDifficultyFilter
        selectedDifficulties={filters.selectedDifficulties}
        onSelectionChange={setters.selectedDifficulties}
      />
    </FilterSection>
  )

  const secondaryFilters = (
    <>
      <FilterSection title={t('filters.floor', 'Floor')} activeCount={filters.selectedFloors.size}>
        <CompactFloorFilter
          selectedFloors={filters.selectedFloors}
          onSelectionChange={setters.selectedFloors}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.egoGift', 'EGO Gift')}
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
          placeholder={t('pages.themePack.searchBar', 'Search Theme Packs...')}
        />
      }
    >
      <ThemePackList
        spec={spec}
        selectedDifficulties={filters.selectedDifficulties}
        selectedFloors={filters.selectedFloors}
        selectedEgoGifts={filters.selectedEgoGifts}
        searchQuery={searchQuery}
      />
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
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="themePack" />}>
        <ThemePackPageShell />
      </Suspense>
    </div>
  )
}
