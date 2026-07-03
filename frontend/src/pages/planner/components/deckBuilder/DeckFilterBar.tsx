import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

import { cn } from '@/lib/utils'
import { calculateActiveFilterCount } from '@/shared/filter'
import { DETAIL_PAGE } from '@/lib/constants'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'

import {
  useDeckFilterState,
  useSetDeckFilterState,
} from '../../stores/usePlannerEditorStore'

import type { EntityMode } from '../../types/DeckTypes'
import type { EGOType } from '@/pages/ego'

import { SearchBar } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { CompactSinnerFilter } from '@/shared/filter'
import { CompactKeywordFilter } from '@/shared/filter'
import { CompactSkillAttributeFilter } from '@/shared/filter'
import { CompactAttackTypeFilter } from '@/shared/filter'
import { CompactDefenseTypeFilter } from '@/shared/filter'
import { CompactRarityFilter } from '@/shared/filter'
import { CompactEGOTypeFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { UnitKeywordDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'

import { EntityToggle } from './EntityToggle'

const FILTER_BOX = 'rounded-md border border-border/60 p-1'

/**
 * Single-row filter bar for the deck builder.
 *
 * Desktop (>= lg): horizontal flex-wrap row of all mode-applicable filters,
 * Search, and Reset All. EntityToggle is the first element.
 *
 * Mobile (< lg): card wrapper with always-visible primary (Toggle + Sinner +
 * Keyword) section; secondary filters collapse behind a centered chevron.
 * SearchBar and Reset All always sit at the bottom.
 *
 * Filter state is read from and written to the planner editor store via
 * useDeckFilterState / useSetDeckFilterState. Inert fields (id-only or
 * ego-only) stay in the store across mode toggles; the UI hides chips for
 * the inactive mode while the predicate in matchesDeckFilter ignores them.
 * Reset All clears every filter set and searchQuery but preserves entityMode.
 */
export function DeckFilterBar() {
  const { t } = useTranslation(['database', 'planner', 'common'])
  const filterState = useDeckFilterState()
  const setFilterState = useSetDeckFilterState()
  const [isExpanded, setIsExpanded] = useState(false)
  const isDesktop = useIsBreakpoint('min', DETAIL_PAGE.BREAKPOINT_LG)

  const activeFilterCount = calculateActiveFilterCount(
    filterState.selectedSinners,
    filterState.selectedKeywords,
    filterState.selectedAttributes,
    filterState.selectedAtkTypes,
    filterState.selectedDefTypes,
    filterState.selectedRaritys,
    filterState.selectedEgoTypes,
    filterState.selectedSeasons,
    filterState.selectedUnitKeywords,
    filterState.selectedBattleKeywords
  )
  const hasSearch = filterState.searchQuery.length > 0
  const hasActiveFilters = activeFilterCount > 0
  const canReset = hasActiveFilters || hasSearch

  const isIdentityMode = filterState.entityMode === 'identity'
  const battleKeywordEntityType = isIdentityMode ? 'identity' : 'ego'

  const handleModeChange = (mode: EntityMode) => {
    setFilterState((prev) => ({ ...prev, entityMode: mode }))
  }

  const handleSinnersChange = (sinners: Set<string>) => {
    setFilterState((prev) => ({ ...prev, selectedSinners: sinners }))
  }

  const handleKeywordsChange = (keywords: Set<string>) => {
    setFilterState((prev) => ({ ...prev, selectedKeywords: keywords }))
  }

  const handleAttributesChange = (attributes: typeof filterState.selectedAttributes) => {
    setFilterState((prev) => ({ ...prev, selectedAttributes: attributes }))
  }

  const handleAtkTypesChange = (types: typeof filterState.selectedAtkTypes) => {
    setFilterState((prev) => ({ ...prev, selectedAtkTypes: types }))
  }

  const handleDefTypesChange = (types: typeof filterState.selectedDefTypes) => {
    setFilterState((prev) => ({ ...prev, selectedDefTypes: types }))
  }

  const handleRaritysChange = (ranks: Set<number>) => {
    setFilterState((prev) => ({ ...prev, selectedRaritys: ranks }))
  }

  const handleEgoTypesChange = (types: Set<string>) => {
    setFilterState((prev) => ({ ...prev, selectedEgoTypes: types as Set<EGOType> }))
  }

  const handleSeasonsChange = (seasons: typeof filterState.selectedSeasons) => {
    setFilterState((prev) => ({ ...prev, selectedSeasons: seasons }))
  }

  const handleUnitKeywordsChange = (unitKeywords: Set<string>) => {
    setFilterState((prev) => ({ ...prev, selectedUnitKeywords: unitKeywords }))
  }

  const handleBattleKeywordsChange = (battleKeywords: Set<string>) => {
    setFilterState((prev) => ({ ...prev, selectedBattleKeywords: battleKeywords }))
  }

  const handleSearchChange = (query: string) => {
    setFilterState((prev) => ({ ...prev, searchQuery: query }))
  }

  const handleResetAll = () => {
    setFilterState((prev) => ({
      ...prev,
      selectedSinners: new Set(),
      selectedKeywords: new Set(),
      selectedAttributes: new Set(),
      selectedAtkTypes: new Set(),
      selectedDefTypes: new Set(),
      selectedRaritys: new Set(),
      selectedEgoTypes: new Set(),
      selectedSeasons: new Set(),
      selectedUnitKeywords: new Set(),
      selectedBattleKeywords: new Set(),
      searchQuery: '',
    }))
  }

  const resetAllButton = (
    <Button
      variant="outline"
      size="sm"
      onClick={handleResetAll}
      disabled={!canReset}
      className={cn(
        canReset && 'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive'
      )}
    >
      {t('filters.resetAll', 'Reset All')}
      {hasActiveFilters && (
        <span className="ml-1 text-muted-foreground">({activeFilterCount})</span>
      )}
    </Button>
  )

  const searchBar = (
    <SearchBar
      searchQuery={filterState.searchQuery}
      onSearchChange={handleSearchChange}
      placeholder={
        isIdentityMode
          ? t('planner:deckBuilder.identitySearchPlaceholder')
          : t('planner:deckBuilder.egoSearchPlaceholder')
      }
    />
  )

  if (isDesktop) {
    return (
      <div
        className={cn(
          'flex flex-wrap items-center gap-2',
          'rounded-lg border bg-card p-2'
        )}
      >
        <EntityToggle mode={filterState.entityMode} onModeChange={handleModeChange} />
        <div className={FILTER_BOX}>
          <CompactSinnerFilter
            selectedSinners={filterState.selectedSinners}
            onSelectionChange={handleSinnersChange}
          />
        </div>
        <div className={FILTER_BOX}>
          <CompactKeywordFilter
            selectedKeywords={filterState.selectedKeywords}
            onSelectionChange={handleKeywordsChange}
          />
        </div>
        <div className={FILTER_BOX}>
          <CompactSkillAttributeFilter
            selectedAttributes={filterState.selectedAttributes}
            onSelectionChange={handleAttributesChange}
          />
        </div>
        <div className={FILTER_BOX}>
          <CompactAttackTypeFilter
            selectedTypes={filterState.selectedAtkTypes}
            onSelectionChange={handleAtkTypesChange}
          />
        </div>
        <div className={FILTER_BOX}>
          {isIdentityMode ? (
            <CompactDefenseTypeFilter
              selectedTypes={filterState.selectedDefTypes}
              onSelectionChange={handleDefTypesChange}
            />
          ) : (
            <CompactEGOTypeFilter
              selectedEGOTypes={filterState.selectedEgoTypes as Set<string>}
              onSelectionChange={handleEgoTypesChange}
            />
          )}
        </div>
        {isIdentityMode && (
          <div className={FILTER_BOX}>
            <CompactRarityFilter
              selectedRaritys={filterState.selectedRaritys}
              onSelectionChange={handleRaritysChange}
            />
          </div>
        )}
        <Suspense fallback={<Skeleton className="h-10 w-48 rounded-md" />}>
          <SeasonDropdown
            selectedSeasons={filterState.selectedSeasons}
            onSelectionChange={handleSeasonsChange}
            className="w-48"
          />
        </Suspense>
        {isIdentityMode && (
          <Suspense fallback={<Skeleton className="h-10 w-48 rounded-md" />}>
            <UnitKeywordDropdown
              selectedUnitKeywords={filterState.selectedUnitKeywords}
              onSelectionChange={handleUnitKeywordsChange}
              className="w-48"
            />
          </Suspense>
        )}
        <Suspense fallback={<Skeleton className="h-10 w-48 rounded-md" />}>
          <BattleKeywordDropdown
            entityType={battleKeywordEntityType}
            selectedBattleKeywords={filterState.selectedBattleKeywords}
            onSelectionChange={handleBattleKeywordsChange}
            className="w-48"
          />
        </Suspense>
        <SearchBar
          searchQuery={filterState.searchQuery}
          onSearchChange={handleSearchChange}
          placeholder={
            isIdentityMode
              ? t('planner:deckBuilder.identitySearchPlaceholder')
              : t('planner:deckBuilder.egoSearchPlaceholder')
          }
          className="h-10 p-1 w-56"
        />
        {resetAllButton}
      </div>
    )
  }

  return (
    <div className="w-full relative">
        <div className="rounded-lg border bg-card p-3">
          <div className="space-y-1">
            <div className="px-1 py-1.5">
              <EntityToggle mode={filterState.entityMode} onModeChange={handleModeChange} />
            </div>

            <FilterSection
              title={t('filters.sinner', 'Sinner')}
              defaultExpanded={true}
              activeCount={filterState.selectedSinners.size}
            >
              <CompactSinnerFilter
                selectedSinners={filterState.selectedSinners}
                onSelectionChange={handleSinnersChange}
              />
            </FilterSection>

            <FilterSection
              title={t('filters.keyword', 'Keyword')}
              defaultExpanded={true}
              activeCount={filterState.selectedKeywords.size}
            >
              <CompactKeywordFilter
                selectedKeywords={filterState.selectedKeywords}
                onSelectionChange={handleKeywordsChange}
              />
            </FilterSection>

            {isExpanded && (
              <>
                <FilterSection
                  title={t('filters.skillAttribute', 'Skill Attribute')}
                  defaultExpanded={false}
                  activeCount={filterState.selectedAttributes.size}
                >
                  <CompactSkillAttributeFilter
                    selectedAttributes={filterState.selectedAttributes}
                    onSelectionChange={handleAttributesChange}
                  />
                </FilterSection>

                <FilterSection
                  title={t('filters.attackType', 'Attack Type')}
                  defaultExpanded={false}
                  activeCount={filterState.selectedAtkTypes.size}
                >
                  <CompactAttackTypeFilter
                    selectedTypes={filterState.selectedAtkTypes}
                    onSelectionChange={handleAtkTypesChange}
                  />
                </FilterSection>

                {isIdentityMode ? (
                  <FilterSection
                    title={t('filters.defenseType', 'Defense Type')}
                    defaultExpanded={false}
                    activeCount={filterState.selectedDefTypes.size}
                  >
                    <CompactDefenseTypeFilter
                      selectedTypes={filterState.selectedDefTypes}
                      onSelectionChange={handleDefTypesChange}
                    />
                  </FilterSection>
                ) : (
                  <FilterSection
                    title={t('filters.egoType', 'EGO Type')}
                    defaultExpanded={false}
                    activeCount={filterState.selectedEgoTypes.size}
                  >
                    <CompactEGOTypeFilter
                      selectedEGOTypes={filterState.selectedEgoTypes as Set<string>}
                      onSelectionChange={handleEgoTypesChange}
                    />
                  </FilterSection>
                )}

                {isIdentityMode && (
                  <FilterSection
                    title={t('filters.rank', 'Rarity')}
                    defaultExpanded={false}
                    activeCount={filterState.selectedRaritys.size}
                  >
                    <CompactRarityFilter
                      selectedRaritys={filterState.selectedRaritys}
                      onSelectionChange={handleRaritysChange}
                    />
                  </FilterSection>
                )}

                <FilterSection
                  title={t('filters.season', 'Season')}
                  defaultExpanded={false}
                  activeCount={filterState.selectedSeasons.size}
                >
                  <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
                    <SeasonDropdown
                      selectedSeasons={filterState.selectedSeasons}
                      onSelectionChange={handleSeasonsChange}
                    />
                  </Suspense>
                </FilterSection>

                {isIdentityMode && (
                  <FilterSection
                    title={t('filters.unitKeywords', 'Unit Keywords')}
                    defaultExpanded={false}
                    activeCount={filterState.selectedUnitKeywords.size}
                  >
                    <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
                      <UnitKeywordDropdown
                        selectedUnitKeywords={filterState.selectedUnitKeywords}
                        onSelectionChange={handleUnitKeywordsChange}
                      />
                    </Suspense>
                  </FilterSection>
                )}

                <FilterSection
                  title={t('filters.additionalKeyword', 'Additional Keywords')}
                  defaultExpanded={false}
                  activeCount={filterState.selectedBattleKeywords.size}
                >
                  <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
                    <BattleKeywordDropdown
                      entityType={battleKeywordEntityType}
                      selectedBattleKeywords={filterState.selectedBattleKeywords}
                      onSelectionChange={handleBattleKeywordsChange}
                    />
                  </Suspense>
                </FilterSection>
              </>
            )}

            <div>{searchBar}</div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleResetAll}
              disabled={!canReset}
              className={cn(
                'w-full',
                canReset && 'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive'
              )}
            >
              {t('filters.resetAll', 'Reset All')}
              {hasActiveFilters && (
                <span className="ml-1 text-muted-foreground">({activeFilterCount})</span>
              )}
            </Button>
          </div>
        </div>

        <button
          type="button"
          onClick={() => { setIsExpanded(!isExpanded) }}
          aria-expanded={isExpanded}
          aria-label={isExpanded ? t('filters.collapse', 'Collapse filters') : t('filters.expand', 'Expand filters')}
          className={cn(
            'absolute left-1/2 bottom-0 -translate-x-1/2 translate-y-1/2',
            'size-8 rounded-full',
            'bg-card border border-border',
            'flex items-center justify-center',
            'hover:bg-accent transition-colors',
            'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2'
          )}
        >
          <ChevronDown
            className={cn(
              'size-4 transition-transform duration-200',
              isExpanded && 'rotate-180'
            )}
          />
      </button>
    </div>
  )
}
