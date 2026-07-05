import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListSpec, EGOList } from '@/pages/ego'
import type { EGOListItem, EGOType } from '@/pages/ego'
import { SearchBar } from '@/shared/filter'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import type { Season, SkillAttributeType, AtkType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import { FilterSection } from '@/shared/filter'
import { CompactSinnerFilter } from '@/shared/filter'
import { CompactKeywordFilter } from '@/shared/filter'
import { CompactAttackTypeFilter } from '@/shared/filter'
import { CompactEGOTypeFilter } from '@/shared/filter'
import { CompactSkillAttributeFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'

import type { z } from 'zod'
import type { EGOSpecListSchema } from '@/pages/ego'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in EGOList (no suspension).
 */
function EGOCardGrid({
  spec,
  selectedSinners,
  selectedKeywords,
  selectedBattleKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedEGOTypes,
  selectedSeasons,
  searchQuery,
}: {
  spec: z.infer<typeof EGOSpecListSchema>
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedBattleKeywords: Set<string>
  selectedAttributes: Set<SkillAttributeType>
  selectedAtkTypes: Set<AtkType>
  selectedEGOTypes: Set<EGOType>
  selectedSeasons: Set<Season>
  searchQuery: string
}) {
  // Build EGOListItem array from spec directly (no transformation needed)
  // Name lookup handled by EGOList's deferred hook
  const egos = useMemo<EGOListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        egoType: specData.egoType,
        skillKeywordList: specData.skillKeywordList,
        battleKeywordList: specData.battleKeywordList,
        attributeTypes: specData.attributeType,
        atkTypes: specData.atkType,
        updateDate: specData.updateDate,
        season: specData.season,
        maxThreadspin: specData.maxThreadspin,
      })),
    [spec],
  )

  return (
    <EGOList
      egos={egos}
      selectedSinners={selectedSinners}
      selectedKeywords={selectedKeywords}
      selectedBattleKeywords={selectedBattleKeywords}
      selectedAttributes={selectedAttributes}
      selectedAtkTypes={selectedAtkTypes}
      selectedEGOTypes={selectedEGOTypes}
      selectedSeasons={selectedSeasons}
      searchQuery={searchQuery}
    />
  )
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function EGOPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useEGOListSpec()

  const seasonCounts = useMemo(() => {
    const sc: Record<string, number> = {}
    for (const entry of Object.values(spec)) {
      const key = String(entry.season)
      sc[key] = (sc[key] ?? 0) + 1
    }
    return sc
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
    selectedEGOTypes: new Set<EGOType>(),
    selectedSeasons: new Set<Season>(),
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

  // Secondary filters (shown when mobile expanded): Skill Attributes, Attack Types, EGO Types, Season
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
        title={t('filters.egoType', 'EGO Type')}
        defaultExpanded={false}
        activeCount={filters.selectedEGOTypes.size}
      >
        <CompactEGOTypeFilter
          selectedEGOTypes={filters.selectedEGOTypes as Set<string>}
          onSelectionChange={(types) => setters.selectedEGOTypes(types as Set<EGOType>)}
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
        title={t('filters.additionalKeyword', 'Additional Keywords')}
        defaultExpanded={false}
        activeCount={filters.selectedBattleKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleKeywordDropdown
            entityType="ego"
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
          placeholder={t('pages.ego.searchBar')}
        />
      }
    >
      {/* No Suspense needed - EGOCardGrid doesn't suspend */}
      <EGOCardGrid
        spec={spec}
        selectedSinners={filters.selectedSinners}
        selectedKeywords={filters.selectedKeywords}
        selectedBattleKeywords={filters.selectedBattleKeywords}
        selectedAttributes={filters.selectedAttributes}
        selectedAtkTypes={filters.selectedAtkTypes}
        selectedEGOTypes={filters.selectedEGOTypes}
        selectedSeasons={filters.selectedSeasons}
        searchQuery={searchQuery}
      />
    </FilterPageLayout>
  )
}

/**
 * EGOPage - EGO browser with responsive filter sidebar
 *
 * Uses FilterPageLayout for responsive desktop sidebar / mobile sheet layout.
 * Title and description remain visible during loading via Suspense boundary.
 *
 * Suspense Strategy:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - Season dropdown: Own Suspense for dropdown i18n
 * - EGOList: Uses deferred hook for name search (no suspension on language change)
 */
export default function EGOPage() {
  return (
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="ego" />}>
        <EGOPageShell />
      </Suspense>
    </div>
  )
}
