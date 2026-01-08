import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListData } from '@/hooks/useEGOListData'
import type { EGO } from '@/types/EGOTypes'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOList } from '@/components/ego/EGOList'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import type { Season } from '@/lib/constants'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactSinnerFilter } from '@/components/filter/CompactSinnerFilter'
import { CompactKeywordFilter } from '@/components/filter/CompactKeywordFilter'
import { CompactAttackTypeFilter } from '@/components/filter/CompactAttackTypeFilter'
import { CompactEGOTypeFilter } from '@/components/filter/CompactEGOTypeFilter'
import { CompactSkillAttributeFilter } from '@/components/filter/CompactSkillAttributeFilter'
import { SeasonDropdown } from '@/components/common/SeasonDropdown'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGOPageContent() {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOListData()

  // Memoize merged EGOs array to prevent re-computation on every render
  // Type assertion needed: Zod validates structure at runtime but outputs string[]
  // while EGO interface expects literal union arrays (Keyword[], SkillAttributeType[], etc.)
  const EGOs = useMemo<EGO[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        name: i18n[id] || id,
        egoType: specData.egoType,
        skillKeywordList: specData.skillKeywordList,
        attributeTypes: specData.attributeType,
        atkTypes: specData.atkType,
        updateDate: specData.updateDate,
        season: specData.season,
      }) as EGO),
    [spec, i18n]
  )

  // Filter states
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedAttributes, setSelectedAttributes] = useState<Set<string>>(new Set())
  const [selectedAtkTypes, setSelectedAtkTypes] = useState<Set<string>>(new Set())
  const [selectedEGOTypes, setSelectedEGOTypes] = useState<Set<string>>(new Set())
  const [selectedSeasons, setSelectedSeasons] = useState<Set<Season>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count
  const activeFilterCount =
    selectedSinners.size +
    selectedKeywords.size +
    selectedAttributes.size +
    selectedAtkTypes.size +
    selectedEGOTypes.size +
    selectedSeasons.size

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
          selectedEGOTypes={selectedEGOTypes}
          onSelectionChange={setSelectedEGOTypes}
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
        <EGOList
          egos={EGOs}
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
 */
export default function EGOPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.ego.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.ego.description')}
      </p>

      <Suspense fallback={<ListPageSkeleton preset="ego" />}>
        <EGOPageContent />
      </Suspense>
    </div>
  )
}
