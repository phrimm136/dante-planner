import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListSpec, IdentityList } from '@/pages/identity'
import type { IdentityListItem, IdentitySpecListSchema, IdentityFacetState } from '@/pages/identity'
import type { z } from 'zod'
import type { Season, SkillAttributeType, AtkType, DefType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { CompactSinnerFilter } from '@/shared/filter'
import { CompactKeywordFilter } from '@/shared/filter'
import { CompactSkillAttributeFilter } from '@/shared/filter'
import { CompactAttackTypeFilter } from '@/shared/filter'
import { CompactDefenseTypeFilter } from '@/shared/filter'
import { CompactRarityFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { UnitKeywordDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in IdentityList (no suspension).
 */
function IdentityCardGrid({
  spec,
  store,
}: {
  spec: z.infer<typeof IdentitySpecListSchema>
  store: FilterStore<IdentityFacetState>
}) {
  // Build IdentityListItem array from spec directly (no transformation needed)
  // Name lookup handled by IdentityList's deferred hook
  const identities: IdentityListItem[] = Object.entries(spec).map(([id, specData]) => ({
    id,
    rank: specData.rank,
    unitKeywordList: specData.unitKeywordList,
    skillKeywordList: specData.skillKeywordList,
    battleKeywordList: specData.battleKeywordList,
    attributeTypes: specData.attributeType,
    atkTypes: specData.atkType,
    defenseTypes: specData.defenseType,
    updateDate: specData.updateDate,
    season: specData.season,
  }))

  return <IdentityList identities={identities} store={store} />
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function IdentityPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useIdentityListSpec()

  // Compute counts for dropdown display
  const { seasonCounts, unitKeywordCounts } = (() => {
    const sc: Record<string, number> = {}
    const ukc: Record<string, number> = {}
    for (const entry of Object.values(spec)) {
      const key = String(entry.season)
      sc[key] = (sc[key] ?? 0) + 1
      for (const kw of entry.unitKeywordList) {
        ukc[kw] = (ukc[kw] ?? 0) + 1
      }
    }
    return { seasonCounts: sc, unitKeywordCounts: ukc }
  })()

  // Filter states
  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useSetFilters({
    selectedSinners: new Set<string>(),
    selectedKeywords: new Set<string>(),
    selectedBattleKeywords: new Set<string>(),
    selectedAttributes: new Set<SkillAttributeType>(),
    selectedAtkTypes: new Set<AtkType>(),
    selectedDefTypes: new Set<DefType>(),
    selectedRaritys: new Set<number>(),
    selectedSeasons: new Set<Season>(),
    selectedUnitKeywords: new Set<string>(),
  })

  // Calculate active filter count for mobile badge
  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  // Primary filters (always visible on mobile): Sinner and Keyword
  const primaryFilters = (
    <>
      <FilterSection
        title={t('filters.sinner', 'Sinner')}
        defaultExpanded={true}
        activeCount={filters.selectedSinners.size}
      >
        <CompactSinnerFilter
          selected={filters.selectedSinners}
          onSelectionChange={setters.selectedSinners}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.keyword', 'Keyword')}
        defaultExpanded={true}
        activeCount={filters.selectedKeywords.size}
      >
        <CompactKeywordFilter
          selected={filters.selectedKeywords}
          onSelectionChange={setters.selectedKeywords}
        />
      </FilterSection>
    </>
  )

  // Secondary filters (shown when mobile expanded): Skill Attribute, Attack Type, Rarity, Season, Unit Keywords
  const secondaryFilters = (
    <>
      <FilterSection
        title={t('filters.skillAttribute', 'Skill Attribute')}
        defaultExpanded={false}
        activeCount={filters.selectedAttributes.size}
      >
        <CompactSkillAttributeFilter
          selected={filters.selectedAttributes}
          onSelectionChange={setters.selectedAttributes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.attackType', 'Attack Type')}
        defaultExpanded={false}
        activeCount={filters.selectedAtkTypes.size}
      >
        <CompactAttackTypeFilter
          selected={filters.selectedAtkTypes}
          onSelectionChange={setters.selectedAtkTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.defenseType', 'Defense Type')}
        defaultExpanded={false}
        activeCount={filters.selectedDefTypes.size}
      >
        <CompactDefenseTypeFilter
          selected={filters.selectedDefTypes}
          onSelectionChange={setters.selectedDefTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.rank', 'Rarity')}
        defaultExpanded={false}
        activeCount={filters.selectedRaritys.size}
      >
        <CompactRarityFilter
          selected={filters.selectedRaritys}
          onSelectionChange={setters.selectedRaritys}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.season', 'Season')}
        defaultExpanded={false}
        activeCount={filters.selectedSeasons.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <SeasonDropdown
            selected={filters.selectedSeasons}
            onSelectionChange={setters.selectedSeasons}
            counts={seasonCounts}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('filters.unitKeywords', 'Unit Keywords')}
        defaultExpanded={false}
        activeCount={filters.selectedUnitKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <UnitKeywordDropdown
            selected={filters.selectedUnitKeywords}
            onSelectionChange={setters.selectedUnitKeywords}
            counts={unitKeywordCounts}
          />
        </Suspense>
      </FilterSection>

      <FilterSection
        title={t('filters.additionalKeyword', 'Additional Keywords')}
        defaultExpanded={false}
        activeCount={filters.selectedBattleKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleKeywordDropdown
            entityType="identity"
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
          placeholder={t('pages.identity.searchBar')}
        />
      }
    >
      {/* No Suspense needed - IdentityCardGrid doesn't suspend */}
      {/* Spec loading is caught by outer ListPageSkeleton */}
      {/* Name search uses deferred hook in IdentityList */}
      <IdentityCardGrid spec={spec} store={store} />
    </FilterPageLayout>
  )
}

/**
 * IdentityPage - Identity browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - Season/UnitKeyword dropdowns: Own Suspense for dropdown i18n
 * - IdentityList: Uses deferred hook for name search (no suspension on language change)
 */
export default function IdentityPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton preset="identity" />}>
      <IdentityPageShell />
    </EntityListPage>
  )
}
