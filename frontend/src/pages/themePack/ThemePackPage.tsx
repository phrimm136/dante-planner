import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/pages/themePack'
import type { DungeonIdx, ThemePackFloor } from '@/lib/constants'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactDungeonDifficultyFilter } from '@/components/filter/CompactDungeonDifficultyFilter'
import { CompactFloorFilter } from '@/components/filter/CompactFloorFilter'
import { EgoGiftSearchDropdown } from '@/components/filter/EgoGiftSearchDropdown'
import { SearchBar } from '@/components/common/SearchBar'
import { ThemePackList } from '@/pages/themePack'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Shell component - loads spec + i18n, manages filter states.
 * Does not suspend on language change since spec query key has no language.
 */
function ThemePackPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const { spec } = useThemePackListData()

  // Filter states
  const [selectedDifficulties, setSelectedDifficulties] = useState<Set<DungeonIdx>>(new Set())
  const [selectedFloors, setSelectedFloors] = useState<Set<ThemePackFloor>>(new Set())
  const [selectedEgoGifts, setSelectedEgoGifts] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  const handleResetAll = () => {
    setSelectedDifficulties(new Set())
    setSelectedFloors(new Set())
    setSelectedEgoGifts(new Set())
    setSearchQuery('')
  }

  const activeFilterCount =
    selectedDifficulties.size +
    selectedFloors.size +
    selectedEgoGifts.size

  const primaryFilters = (
    <FilterSection
      title={t('filters.difficulty', 'Difficulty')}
      activeCount={selectedDifficulties.size}
    >
      <CompactDungeonDifficultyFilter
        selectedDifficulties={selectedDifficulties}
        onSelectionChange={setSelectedDifficulties}
      />
    </FilterSection>
  )

  const secondaryFilters = (
    <>
      <FilterSection
        title={t('filters.floor', 'Floor')}
        activeCount={selectedFloors.size}
      >
        <CompactFloorFilter
          selectedFloors={selectedFloors}
          onSelectionChange={setSelectedFloors}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.egoGift', 'EGO Gift')}
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
          placeholder={t('pages.themePack.searchBar', 'Search Theme Packs...')}
        />
      }
    >
      <ThemePackList
        spec={spec}
        selectedDifficulties={selectedDifficulties}
        selectedFloors={selectedFloors}
        selectedEgoGifts={selectedEgoGifts}
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
