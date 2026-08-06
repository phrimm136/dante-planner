import { useTranslation } from 'react-i18next'
import { useEGOGiftListSpec } from '@/pages/egoGift'
import type { EGOGiftListItem, EGOGiftSpecListSchema, EGOGiftFacetState } from '@/pages/egoGift'
import type { z } from 'zod'
import type { EGOGiftDifficulty, EGOGiftTier, EGOGiftAttributeType } from '@/shared/gameData'
import { BOOLEAN_FILTER_OPTIONS } from '@/lib/constants'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { CompactEGOGiftKeywordFilter } from '@/pages/egoGift'
import { CompactDifficultyFilter } from '@/pages/egoGift'
import { CompactTierFilter } from '@/pages/egoGift'
import { ThemePackFilterDropdown } from '@/pages/themePack'
import { BattleKeywordDropdown } from '@/shared/filter'
import { CompactAttributeTypeFilter } from '@/shared/filter'
import { CompactIconFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { EGOGiftList } from '@/pages/egoGift'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'

/** The Yes/No icon filter both boolean gift facets render. */
function BooleanFilter({
  selected,
  onSelectionChange,
}: {
  selected: Set<string>
  onSelectionChange: (options: Set<string>) => void
}) {
  return (
    <CompactIconFilter
      options={BOOLEAN_FILTER_OPTIONS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getLabel={(v) => v}
    />
  )
}

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in EGOGiftList (no suspension).
 */
function EGOGiftCardGrid({
  spec,
  store,
}: {
  spec: z.infer<typeof EGOGiftSpecListSchema>
  store: FilterStore<EGOGiftFacetState>
}) {
  // Build EGOGiftListItem array from spec directly
  // Name lookup handled by EGOGiftList's deferred hook
  const gifts: EGOGiftListItem[] = Object.entries(spec).map(([id, specData]) => ({
    id,
    tag: specData.tag as EGOGiftListItem['tag'],
    keyword: specData.keyword,
    battleKeywordList: specData.battleKeywordList,
    attributeType: specData.attributeType,
    themePack: specData.themePack,
    maxEnhancement: specData.maxEnhancement,
    hardOnly: specData.hardOnly,
    extremeOnly: specData.extremeOnly,
    fusioned: specData.fusioned,
  }))

  return <EGOGiftList gifts={gifts} store={store} />
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function EGOGiftPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useEGOGiftListSpec()

  // Filter states
  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useSetFilters({
    selectedKeywords: new Set<string>(),
    selectedBattleKeywords: new Set<string>(),
    selectedDifficulties: new Set<EGOGiftDifficulty>(),
    selectedTiers: new Set<EGOGiftTier>(),
    selectedThemePacks: new Set<string>(),
    selectedAttributeTypes: new Set<EGOGiftAttributeType>(),
    selectedFusioned: new Set<string>(),
    selectedExclusive: new Set<string>(),
  })

  // Calculate active filter count for mobile badge
  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  // Primary filters (always visible on mobile): Keyword and Difficulty
  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedKeywords',
      titleKey: 'filters.keyword',
      titleFallback: 'Keyword',
      Component: CompactEGOGiftKeywordFilter,
      selected: filters.selectedKeywords,
      onSelectionChange: setters.selectedKeywords,
    }),
    filterSection({
      key: 'selectedDifficulties',
      titleKey: 'filters.difficulty',
      titleFallback: 'Difficulty',
      Component: CompactDifficultyFilter,
      selected: filters.selectedDifficulties,
      onSelectionChange: setters.selectedDifficulties,
    }),
  ]

  // Secondary filters (shown when mobile expanded): Tier, Theme Pack, Attribute Type
  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedTiers',
      titleKey: 'filters.tier',
      titleFallback: 'Tier',
      Component: CompactTierFilter,
      selected: filters.selectedTiers,
      onSelectionChange: setters.selectedTiers,
    }),
    filterSection({
      key: 'selectedAttributeTypes',
      titleKey: 'filters.attributeType',
      titleFallback: 'Attribute',
      Component: CompactAttributeTypeFilter,
      selected: filters.selectedAttributeTypes,
      onSelectionChange: setters.selectedAttributeTypes,
    }),
    filterSection({
      key: 'selectedFusioned',
      titleKey: 'filters.fusioned',
      titleFallback: 'Fusioned',
      Component: BooleanFilter,
      selected: filters.selectedFusioned,
      onSelectionChange: setters.selectedFusioned,
    }),
    filterSection({
      key: 'selectedExclusive',
      titleKey: 'filters.themePackExclusive',
      titleFallback: 'Theme Pack Exclusive',
      Component: BooleanFilter,
      selected: filters.selectedExclusive,
      onSelectionChange: setters.selectedExclusive,
    }),
    filterSection({
      key: 'selectedThemePacks',
      titleKey: 'filters.themePack',
      titleFallback: 'Theme Pack',
      suspense: true,
      Component: ThemePackFilterDropdown,
      selected: filters.selectedThemePacks,
      onSelectionChange: setters.selectedThemePacks,
    }),
    filterSection({
      key: 'selectedBattleKeywords',
      titleKey: 'filters.additionalKeyword',
      titleFallback: 'Additional Keywords',
      suspense: true,
      Component: BattleKeywordDropdown,
      selected: filters.selectedBattleKeywords,
      onSelectionChange: setters.selectedBattleKeywords,
      props: { entityType: 'egoGift' as const },
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
          placeholder={t('pages.egoGift.searchBar', 'Search EGO Gifts...')}
        />
      }
    >
      {/* No Suspense needed - EGOGiftCardGrid doesn't suspend */}
      {/* Spec loading is caught by outer ListPageSkeleton */}
      {/* Name search uses deferred hook in EGOGiftList */}
      <EGOGiftCardGrid spec={spec} store={store} />
    </FilterPageLayout>
  )
}

/**
 * EGOGiftPage - EGO Gift browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - Theme pack dropdown: Own Suspense for dropdown i18n
 * - EGOGiftList: Uses deferred hook for name search (no suspension on language change)
 */
export default function EGOGiftPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton preset="egoGift" />}>
      <EGOGiftPageShell />
    </EntityListPage>
  )
}
