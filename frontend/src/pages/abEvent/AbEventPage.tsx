import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ThemePackFilterDropdown } from '@/pages/themePack'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
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
    store,
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
        <EGOGiftFilterDropdown
          selected={filters.selectedEgoGifts}
          onSelectionChange={setters.selectedEgoGifts}
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
        <ThemePackFilterDropdown
          selected={filters.selectedThemePacks}
          onThemePacksChange={setters.selectedThemePacks}
        />
      </Suspense>
    </FilterSection>
  )

  return (
    <FilterPageLayout
      primaryFilters={primaryFilters}
      secondaryFilters={secondaryFilters}
      activeFilterCount={activeFilterCount}
      onResetAll={resetAll}
    >
      <AbEventList spec={spec} store={store} />
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
    <EntityListPage skeleton={<ListPageSkeleton preset="abEvent" />}>
      <AbEventPageShell />
    </EntityListPage>
  )
}
