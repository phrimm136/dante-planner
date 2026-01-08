import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import type { IdentityListItem } from '@/types/IdentityTypes'
import type { IdentitySpecListSchema } from '@/schemas'
import type { z } from 'zod'
import type { Season } from '@/lib/constants'
import { calculateActiveFilterCount } from '@/lib/filterUtils'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactSinnerFilter } from '@/components/filter/CompactSinnerFilter'
import { CompactKeywordFilter } from '@/components/filter/CompactKeywordFilter'
import { CompactSkillAttributeFilter } from '@/components/filter/CompactSkillAttributeFilter'
import { CompactAttackTypeFilter } from '@/components/filter/CompactAttackTypeFilter'
import { CompactRarityFilter } from '@/components/filter/CompactRarityFilter'
import { SeasonDropdown } from '@/components/common/SeasonDropdown'
import { UnitKeywordDropdown } from '@/components/common/UnitKeywordDropdown'
import { SearchBar } from '@/components/common/SearchBar'
import { IdentityList } from '@/components/identity/IdentityList'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in IdentityList (no suspension).
 */
function IdentityCardGrid({
  spec,
  selectedSinners,
  selectedKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedRaritys,
  selectedSeasons,
  selectedUnitKeywords,
  searchQuery,
}: {
  spec: z.infer<typeof IdentitySpecListSchema>
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<string>
  selectedAtkTypes: Set<string>
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
        attributeTypes: specData.attributeType,
        atkTypes: specData.atkType,
        updateDate: specData.updateDate,
        season: specData.season,
      })),
    [spec]
  )

  return (
    <IdentityList
      identities={identities}
      selectedSinners={selectedSinners}
      selectedKeywords={selectedKeywords}
      selectedAttributes={selectedAttributes}
      selectedAtkTypes={selectedAtkTypes}
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
  const { t } = useTranslation()
  const spec = useIdentityListSpec()

  // Filter states
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedAttributes, setSelectedAttributes] = useState<Set<string>>(new Set())
  const [selectedAtkTypes, setSelectedAtkTypes] = useState<Set<string>>(new Set())
  const [selectedRaritys, setSelectedRaritys] = useState<Set<number>>(new Set())
  const [selectedSeasons, setSelectedSeasons] = useState<Set<Season>>(new Set())
  const [selectedUnitKeywords, setSelectedUnitKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count for mobile badge
  const activeFilterCount = calculateActiveFilterCount(
    selectedSinners,
    selectedKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedRaritys,
    selectedSeasons,
    selectedUnitKeywords
  )

  // Reset all filters
  const handleResetAll = () => {
    setSelectedSinners(new Set())
    setSelectedKeywords(new Set())
    setSelectedAttributes(new Set())
    setSelectedAtkTypes(new Set())
    setSelectedRaritys(new Set())
    setSelectedSeasons(new Set())
    setSelectedUnitKeywords(new Set())
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

  // Secondary filters (shown when mobile expanded): Skill Attribute, Attack Type, Rarity, Season, Unit Keywords
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
        title={t('filters.rank', 'Rarity')}
        defaultExpanded={false}
        activeCount={selectedRaritys.size}
      >
        <CompactRarityFilter
          selectedRaritys={selectedRaritys}
          onSelectionChange={setSelectedRaritys}
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

      <FilterSection
        title={t('filters.unitKeywords', 'Unit Keywords')}
        defaultExpanded={false}
        activeCount={selectedUnitKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <UnitKeywordDropdown
            selectedUnitKeywords={selectedUnitKeywords}
            onSelectionChange={setSelectedUnitKeywords}
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
        selectedSinners={selectedSinners}
        selectedKeywords={selectedKeywords}
        selectedAttributes={selectedAttributes}
        selectedAtkTypes={selectedAtkTypes}
        selectedRaritys={selectedRaritys}
        selectedSeasons={selectedSeasons}
        selectedUnitKeywords={selectedUnitKeywords}
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
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.identity.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.identity.description')}
      </p>

      <Suspense fallback={<ListPageSkeleton preset="identity" />}>
        <IdentityPageShell />
      </Suspense>
    </div>
  )
}
