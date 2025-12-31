import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListData } from '@/hooks/useEGOListData'
import type { EGO } from '@/types/EGOTypes'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOList } from '@/components/ego/EGOList'
import { LoadingState } from '@/components/common/LoadingState'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'
import type { Season } from '@/lib/constants'
import { FilterSection } from '@/components/common/FilterSection'
import { CompactSinnerFilter } from '@/components/common/CompactSinnerFilter'
import { CompactKeywordFilter } from '@/components/common/CompactKeywordFilter'
import { CompactAttackTypeFilter } from '@/components/common/CompactAttackTypeFilter'
import { CompactEGOTypeFilter } from '@/components/common/CompactEGOTypeFilter'
import { CompactSkillAttributeFilter } from '@/components/common/CompactSkillAttributeFilter'
import { SeasonDropdown } from '@/components/common/SeasonDropdown'
import { FilterPageLayout } from '@/components/common/FilterPageLayout'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGOPageContent() {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOListData()
  const { seasonsI18n } = useFilterI18nData()

  // Memoize merged EGOs array to prevent re-computation on every render
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
      })),
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
        title={t('filters.rank', 'Rank')}
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
        <SeasonDropdown
          selectedSeasons={selectedSeasons}
          onSelectionChange={setSelectedSeasons}
          seasonsI18n={seasonsI18n}
        />
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
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.ego.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.ego.description')}
      </p>

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
          searchQuery={searchQuery}
        />
      </FilterPageLayout>
    </div>
  )
}

export default function EGOPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <EGOPageContent />
    </Suspense>
  )
}
