import { Suspense } from 'react'
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
import { FilterSection } from '@/shared/filter'
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
import { Skeleton } from '@/components/ui/skeleton'

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
  const primaryFilters = (
    <>
      <FilterSection
        title={t('filters.keyword', 'Keyword')}
        activeCount={filters.selectedKeywords.size}
      >
        <CompactEGOGiftKeywordFilter
          selectedKeywords={filters.selectedKeywords}
          onSelectionChange={setters.selectedKeywords}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.difficulty', 'Difficulty')}
        activeCount={filters.selectedDifficulties.size}
      >
        <CompactDifficultyFilter
          selectedDifficulties={filters.selectedDifficulties}
          onSelectionChange={setters.selectedDifficulties}
        />
      </FilterSection>
    </>
  )

  // Secondary filters (shown when mobile expanded): Tier, Theme Pack, Attribute Type
  const secondaryFilters = (
    <>
      <FilterSection title={t('filters.tier', 'Tier')} activeCount={filters.selectedTiers.size}>
        <CompactTierFilter
          selectedTiers={filters.selectedTiers}
          onSelectionChange={setters.selectedTiers}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.attributeType', 'Attribute')}
        activeCount={filters.selectedAttributeTypes.size}
      >
        <CompactAttributeTypeFilter
          selected={filters.selectedAttributeTypes}
          onAttributeTypesChange={setters.selectedAttributeTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.fusioned', 'Fusioned')}
        activeCount={filters.selectedFusioned.size}
      >
        <CompactIconFilter
          options={BOOLEAN_FILTER_OPTIONS}
          selectedOptions={filters.selectedFusioned}
          onSelectionChange={setters.selectedFusioned}
          getLabel={(v) => v}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.themePackExclusive', 'Theme Pack Exclusive')}
        activeCount={filters.selectedExclusive.size}
      >
        <CompactIconFilter
          options={BOOLEAN_FILTER_OPTIONS}
          selectedOptions={filters.selectedExclusive}
          onSelectionChange={setters.selectedExclusive}
          getLabel={(v) => v}
        />
      </FilterSection>

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

      <FilterSection
        title={t('filters.additionalKeyword', 'Additional Keywords')}
        activeCount={filters.selectedBattleKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleKeywordDropdown
            entityType="egoGift"
            selected={filters.selectedBattleKeywords}
            onSelectionChange={setters.selectedBattleKeywords}
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
