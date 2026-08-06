import { ThemePackFilterDropdown } from '@/pages/themePack'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { EGOGiftFilterDropdown } from '@/pages/egoGift'
import { AbEventList, useAbEventListSpec } from '@/pages/abEvent'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'

/**
 * Shell component - loads spec, manages filter states.
 */
function AbEventPageShell() {
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
