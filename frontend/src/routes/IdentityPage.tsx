import { useState, Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'
import type { Identity } from '@/types/IdentityTypes'
import { FilterPageLayout } from '@/components/common/FilterPageLayout'
import { FilterSection } from '@/components/common/FilterSection'
import { CompactSinnerFilter } from '@/components/common/CompactSinnerFilter'
import { CompactKeywordFilter } from '@/components/common/CompactKeywordFilter'
import { CompactSkillAttributeFilter } from '@/components/common/CompactSkillAttributeFilter'
import { CompactAttackTypeFilter } from '@/components/common/CompactAttackTypeFilter'
import { CompactRankFilter } from '@/components/common/CompactRankFilter'
import { SeasonDropdown } from '@/components/common/SeasonDropdown'
import { AssociationDropdown } from '@/components/common/AssociationDropdown'
import { SearchBar } from '@/components/common/SearchBar'
import { IdentityList } from '@/components/identity/IdentityList'
import { LoadingState } from '@/components/common/LoadingState'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function IdentityPageContent() {
  const { t } = useTranslation()
  const { spec, i18n } = useIdentityListData()
  const { seasonsI18n, associationsI18n } = useFilterI18nData()

  // Merge spec and i18n into Identity array with new fields
  const identities: Identity[] = Object.entries(spec).map(([id, specData]) => ({
    id,
    name: i18n[id] || id,
    rank: specData.rank,
    unitKeywordList: specData.unitKeywordList,
    skillKeywordList: specData.skillKeywordList,
    attributeTypes: specData.attributeType,
    atkTypes: specData.atkType,
    season: specData.season,
    associationList: specData.associationList,
  }))

  // Filter states
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedAttributes, setSelectedAttributes] = useState<Set<string>>(new Set())
  const [selectedAtkTypes, setSelectedAtkTypes] = useState<Set<string>>(new Set())
  const [selectedRanks, setSelectedRanks] = useState<Set<number>>(new Set())
  const [selectedSeasons, setSelectedSeasons] = useState<Set<number>>(new Set())
  const [selectedAssociations, setSelectedAssociations] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count for mobile badge
  const activeFilterCount =
    selectedSinners.size +
    selectedKeywords.size +
    selectedAttributes.size +
    selectedAtkTypes.size +
    selectedRanks.size +
    selectedSeasons.size +
    selectedAssociations.size

  // Reset all filters
  const handleResetAll = () => {
    setSelectedSinners(new Set())
    setSelectedKeywords(new Set())
    setSelectedAttributes(new Set())
    setSelectedAtkTypes(new Set())
    setSelectedRanks(new Set())
    setSelectedSeasons(new Set())
    setSelectedAssociations(new Set())
    setSearchQuery('')
  }

  // Cleanup: reset filters on unmount to prevent stale state on navigation back
  useEffect(() => {
    return () => {
      handleResetAll()
    }
  }, [])

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

  // Secondary filters (shown when mobile expanded): Skill Attribute, Attack Type, Rank, Season, Association
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
        activeCount={selectedRanks.size}
      >
        <CompactRankFilter
          selectedRanks={selectedRanks}
          onSelectionChange={setSelectedRanks}
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

      <FilterSection
        title={t('filters.association', 'Association')}
        defaultExpanded={false}
        activeCount={selectedAssociations.size}
      >
        <AssociationDropdown
          selectedAssociations={selectedAssociations}
          onSelectionChange={setSelectedAssociations}
          associationsI18n={associationsI18n}
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
      <h1 className="text-3xl font-bold mb-4">{t('pages.identity.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.identity.description')}
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
            placeholder={t('pages.identity.searchBar')}
          />
        }
      >
        <IdentityList
          identities={identities}
          selectedSinners={selectedSinners}
          selectedKeywords={selectedKeywords}
          selectedAttributes={selectedAttributes}
          selectedAtkTypes={selectedAtkTypes}
          selectedRanks={selectedRanks}
          selectedSeasons={selectedSeasons}
          selectedAssociations={selectedAssociations}
          searchQuery={searchQuery}
        />
      </FilterPageLayout>
    </div>
  )
}

/**
 * IdentityPage - Identity browser with responsive filter sidebar
 *
 * Uses FilterPageLayout for responsive desktop sidebar / mobile sheet layout
 */
export default function IdentityPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityPageContent />
    </Suspense>
  )
}
