import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/pages/themePack'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { ThemePackDropdown } from '@/shared/filter'
import { EgoGiftSearchDropdown } from '@/shared/filter'
import { useEGOGiftListData } from '@/pages/egoGift'
import { AbEventList, useAbEventListSpec } from '@/pages/abEvent'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Shell component - loads spec, manages filter states.
 */
function AbEventPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useAbEventListSpec()

  // Filter states
  const {
    values: filters,
    setters,
    resetAll,
  } = useSetFilters({
    selectedEgoGifts: new Set<string>(),
    selectedThemePacks: new Set<string>(),
  })

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const primaryFilters = (
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
  )

  const secondaryFilters = (
    <FilterSection
      title={t('filters.themePack', 'Theme Pack')}
      activeCount={filters.selectedThemePacks.size}
    >
      <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
        <ThemePackDropdown
          selectedThemePacks={filters.selectedThemePacks}
          onThemePacksChange={setters.selectedThemePacks}
          useListData={useThemePackListData}
        />
      </Suspense>
    </FilterSection>
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
      onResetAll={resetAll}
    >
      <AbEventList
        spec={spec}
        selectedEgoGifts={filters.selectedEgoGifts}
        selectedThemePacks={filters.selectedThemePacks}
      />
    </FilterPageLayout>
  )
}

/**
 * AbEventPage - Abnormality event browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - EGO Gift / Theme Pack dropdowns: Own Suspense for i18n
 * - AbEventList: Uses deferred hook for name search
 */
export default function AbEventPage() {
  return (
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="abEvent" />}>
        <AbEventPageShell />
      </Suspense>
    </div>
  )
}
