import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListSpec } from '@/hooks/useEGOListData'
import type { EGOListItem, EGOType } from '@/types/EGOTypes'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOList } from '@/components/ego/EGOList'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import type { Season } from '@/lib/constants'
import { calculateActiveFilterCount } from '@/lib/filterUtils'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactSinnerFilter } from '@/components/filter/CompactSinnerFilter'
import { CompactKeywordFilter } from '@/components/filter/CompactKeywordFilter'
import { CompactAttackTypeFilter } from '@/components/filter/CompactAttackTypeFilter'
import { CompactEGOTypeFilter } from '@/components/filter/CompactEGOTypeFilter'
import { CompactSkillAttributeFilter } from '@/components/filter/CompactSkillAttributeFilter'
import { SeasonDropdown } from '@/components/common/SeasonDropdown'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'

import type { z } from 'zod'
import type { EGOSpecListSchema } from '@/schemas'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in EGOList (no suspension).
 */
function EGOCardGrid({
  spec,
  selectedSinners,
  selectedKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedEGOTypes,
  selectedSeasons,
  searchQuery,
}: {
  spec: z.infer<typeof EGOSpecListSchema>
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<string>
  selectedAtkTypes: Set<string>
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
        attributeTypes: specData.attributeType,
        atkTypes: specData.atkType,
        updateDate: specData.updateDate,
        season: specData.season,
      })),
    [spec]
  )

  return (
    <EGOList
      egos={egos}
      selectedSinners={selectedSinners}
      selectedKeywords={selectedKeywords}
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

  // Filter states
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedAttributes, setSelectedAttributes] = useState<Set<string>>(new Set())
  const [selectedAtkTypes, setSelectedAtkTypes] = useState<Set<string>>(new Set())
  const [selectedEGOTypes, setSelectedEGOTypes] = useState<Set<EGOType>>(new Set())
  const [selectedSeasons, setSelectedSeasons] = useState<Set<Season>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count for mobile badge
  const activeFilterCount = calculateActiveFilterCount(
    selectedSinners,
    selectedKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedEGOTypes,
    selectedSeasons
  )

  // Reset all filters
  const handleResetAll = () => {
    setSelectedSinners(new Set())
    setSelectedKeywords(new Set())
    setSelectedAttributes(new Set())
    setSelectedAtkTypes(new Set())
    setSelectedEGOTypes(new Set())
    setSelectedSeasons(new Set())
    setSearchQuery('')
  }

  // Primary filters (always visible on mobile): Sinner and Keyword
  const primaryFilters = (
    <>
      <FilterSection
        title={t('filters.sinner', 'Sinner')}
        defaultExpanded={true}
        activeCount={selectedSinners.size}
      >
        <CompactSinnerFilter
          selectedSinners={selectedSinners}
          onSelectionChange={setSelectedSinners}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.keyword', 'Keyword')}
        defaultExpanded={true}
        activeCount={selectedKeywords.size}
      >
        <CompactKeywordFilter
          selectedKeywords={selectedKeywords}
          onSelectionChange={setSelectedKeywords}
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
        activeCount={selectedAttributes.size}
      >
        <CompactSkillAttributeFilter
          selectedAttributes={selectedAttributes}
          onSelectionChange={setSelectedAttributes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.attackType', 'Attack Type')}
        defaultExpanded={false}
        activeCount={selectedAtkTypes.size}
      >
        <CompactAttackTypeFilter
          selectedTypes={selectedAtkTypes}
          onSelectionChange={setSelectedAtkTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.egoType', 'EGO Type')}
        defaultExpanded={false}
        activeCount={selectedEGOTypes.size}
      >
        <CompactEGOTypeFilter
          selectedEGOTypes={selectedEGOTypes as Set<string>}
          onSelectionChange={(types) => setSelectedEGOTypes(types as Set<EGOType>)}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.season', 'Season')}
        defaultExpanded={false}
        activeCount={selectedSeasons.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <SeasonDropdown
            selectedSeasons={selectedSeasons}
            onSelectionChange={setSelectedSeasons}
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
          selectedSinners={selectedSinners}
          selectedKeywords={selectedKeywords}
          selectedAttributes={selectedAttributes}
          selectedAtkTypes={selectedAtkTypes}
          selectedEGOTypes={selectedEGOTypes}
          selectedSeasons={selectedSeasons}
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
