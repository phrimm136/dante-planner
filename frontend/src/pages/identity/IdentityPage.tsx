import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListSpec, IdentityList } from '@/pages/identity'
import type { IdentityListItem, IdentitySpecListSchema } from '@/pages/identity'
import type { z } from 'zod'
import type { Season, SkillAttributeType, AtkType, DefType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
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
  selectedSinners,
  selectedKeywords,
  selectedBattleKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedDefTypes,
  selectedRaritys,
  selectedSeasons,
  selectedUnitKeywords,
  searchQuery,
}: {
  spec: z.infer<typeof IdentitySpecListSchema>
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedBattleKeywords: Set<string>
  selectedAttributes: Set<SkillAttributeType>
  selectedAtkTypes: Set<AtkType>
  selectedDefTypes: Set<DefType>
  selectedRaritys: Set<number>
  selectedSeasons: Set<Season>
  selectedUnitKeywords: Set<string>
  searchQuery: string
}) {
  // Build IdentityListItem array from spec directly (no transformation needed)
  // Name lookup handled by IdentityList's deferred hook
  const identities = useMemo<IdentityListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
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
      })),
    [spec],
  )

  return (
    <IdentityList
      identities={identities}
      selectedSinners={selectedSinners}
      selectedKeywords={selectedKeywords}
      selectedBattleKeywords={selectedBattleKeywords}
      selectedAttributes={selectedAttributes}
      selectedAtkTypes={selectedAtkTypes}
      selectedDefTypes={selectedDefTypes}
      selectedRaritys={selectedRaritys}
      selectedSeasons={selectedSeasons}
      selectedUnitKeywords={selectedUnitKeywords}
      searchQuery={searchQuery}
    />
  )
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function IdentityPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useIdentityListSpec()

  // Compute counts for dropdown display
  const { seasonCounts, unitKeywordCounts } = useMemo(() => {
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
  }, [spec])

  // Filter states
  const {
    values: filters,
    setters,
    resetAll,
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
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count for mobile badge
  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  // Reset all filters
  const handleResetAll = () => {
    resetAll()
    setSearchQuery('')
  }

  // Primary filters (always visible on mobile): Sinner and Keyword
  const primaryFilters = (
    <>
      <FilterSection
        title={t('filters.sinner', 'Sinner')}
        defaultExpanded={true}
        activeCount={filters.selectedSinners.size}
      >
        <CompactSinnerFilter
          selectedSinners={filters.selectedSinners}
          onSelectionChange={setters.selectedSinners}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.keyword', 'Keyword')}
        defaultExpanded={true}
        activeCount={filters.selectedKeywords.size}
      >
        <CompactKeywordFilter
          selectedKeywords={filters.selectedKeywords}
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
          selectedAttributes={filters.selectedAttributes}
          onSelectionChange={setters.selectedAttributes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.attackType', 'Attack Type')}
        defaultExpanded={false}
        activeCount={filters.selectedAtkTypes.size}
      >
        <CompactAttackTypeFilter
          selectedTypes={filters.selectedAtkTypes}
          onSelectionChange={setters.selectedAtkTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.defenseType', 'Defense Type')}
        defaultExpanded={false}
        activeCount={filters.selectedDefTypes.size}
      >
        <CompactDefenseTypeFilter
          selectedTypes={filters.selectedDefTypes}
          onSelectionChange={setters.selectedDefTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.rank', 'Rarity')}
        defaultExpanded={false}
        activeCount={filters.selectedRaritys.size}
      >
        <CompactRarityFilter
          selectedRaritys={filters.selectedRaritys}
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
            selectedSeasons={filters.selectedSeasons}
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
            selectedUnitKeywords={filters.selectedUnitKeywords}
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
            selectedBattleKeywords={filters.selectedBattleKeywords}
            onSelectionChange={setters.selectedBattleKeywords}
          />
        </Suspense>
      </FilterSection>
    </>
  )

  // Combined filter content for desktop sidebar (all filters together)
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
          placeholder={t('pages.identity.searchBar')}
        />
      }
    >
      {/* No Suspense needed - IdentityCardGrid doesn't suspend */}
      {/* Spec loading is caught by outer ListPageSkeleton */}
      {/* Name search uses deferred hook in IdentityList */}
      <IdentityCardGrid
        spec={spec}
        selectedSinners={filters.selectedSinners}
        selectedKeywords={filters.selectedKeywords}
        selectedBattleKeywords={filters.selectedBattleKeywords}
        selectedAttributes={filters.selectedAttributes}
        selectedAtkTypes={filters.selectedAtkTypes}
        selectedDefTypes={filters.selectedDefTypes}
        selectedRaritys={filters.selectedRaritys}
        selectedSeasons={filters.selectedSeasons}
        selectedUnitKeywords={filters.selectedUnitKeywords}
        searchQuery={searchQuery}
      />
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
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="identity" />}>
        <IdentityPageShell />
      </Suspense>
    </div>
  )
}
