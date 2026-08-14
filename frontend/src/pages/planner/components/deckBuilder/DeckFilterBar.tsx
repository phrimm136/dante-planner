import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

import { cn } from '@/lib/utils'
import { calculateActiveFilterCount } from '@/shared/filter'
import { DETAIL_PAGE } from '@/lib/constants'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'

import { useDeckFilterState, useSetDeckFilterState } from '../../stores/usePlannerEditorStore'

import type { ComponentType } from 'react'
import type { DeckFilterState, EntityMode } from '../../types/DeckTypes'
import type { EgoType } from '@/shared/gameData'

import { SearchBar } from '@/shared/filter'
import { FilterSection } from '@/shared/filter'
import { SinnerFilter } from '@/shared/filter'
import { KeywordFilter } from '@/shared/filter'
import { SkillAttributeFilter } from '@/shared/filter'
import { AttackTypeFilter } from '@/shared/filter'
import { DefenseTypeFilter } from '@/shared/filter'
import { RarityFilter } from '@/shared/filter'
import { EGOTypeFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { UnitKeywordDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'

import { EntityToggle } from './EntityToggle'

const FILTER_BOX = 'rounded-md border border-border/60 p-1'

/** Writes one field of the filter state, leaving the rest alone. */
type SetFilterField = <K extends keyof DeckFilterState>(
  key: K,
) => (value: DeckFilterState[K]) => void

/** The filter-state fields a filter chip owns: all of them hold a selection set. */
type FilterSetKey = {
  [K in keyof DeckFilterState]: DeckFilterState[K] extends Set<unknown> ? K : never
}[keyof DeckFilterState]

interface FilterControlProps {
  state: DeckFilterState
  setField: SetFilterField
  /** Applied to dropdown controls, which size themselves per layout. */
  className?: string
}

function SinnerControl({ state, setField }: FilterControlProps) {
  return (
    <SinnerFilter
      selected={state.selectedSinners}
      onSelectionChange={setField('selectedSinners')}
    />
  )
}

function KeywordControl({ state, setField }: FilterControlProps) {
  return (
    <KeywordFilter
      selected={state.selectedKeywords}
      onSelectionChange={setField('selectedKeywords')}
    />
  )
}

function SkillAttributeControl({ state, setField }: FilterControlProps) {
  return (
    <SkillAttributeFilter
      selected={state.selectedAttributes}
      onSelectionChange={setField('selectedAttributes')}
    />
  )
}

function AttackTypeControl({ state, setField }: FilterControlProps) {
  return (
    <AttackTypeFilter
      selected={state.selectedAtkTypes}
      onSelectionChange={setField('selectedAtkTypes')}
    />
  )
}

function DefenseTypeControl({ state, setField }: FilterControlProps) {
  return (
    <DefenseTypeFilter
      selected={state.selectedDefTypes}
      onSelectionChange={setField('selectedDefTypes')}
    />
  )
}

function EGOTypeControl({ state, setField }: FilterControlProps) {
  const setEgoTypes = setField('selectedEgoTypes')

  return (
    <EGOTypeFilter
      selected={state.selectedEgoTypes as Set<string>}
      onSelectionChange={(types) => {
        setEgoTypes(types as Set<EgoType>)
      }}
    />
  )
}

function RarityControl({ state, setField }: FilterControlProps) {
  return (
    <RarityFilter
      selected={state.selectedRaritys}
      onSelectionChange={setField('selectedRaritys')}
    />
  )
}

function SeasonControl({ state, setField, className }: FilterControlProps) {
  return (
    <SeasonDropdown
      selected={state.selectedSeasons}
      onSelectionChange={setField('selectedSeasons')}
      className={className}
    />
  )
}

function UnitKeywordControl({ state, setField, className }: FilterControlProps) {
  return (
    <UnitKeywordDropdown
      selected={state.selectedUnitKeywords}
      onSelectionChange={setField('selectedUnitKeywords')}
      className={className}
    />
  )
}

function BattleKeywordControl({ state, setField, className }: FilterControlProps) {
  return (
    <BattleKeywordDropdown
      entityType={state.entityMode === 'identity' ? 'identity' : 'ego'}
      selected={state.selectedBattleKeywords}
      onSelectionChange={setField('selectedBattleKeywords')}
      className={className}
    />
  )
}

interface DeckFilter {
  /** Selection set this filter reads its active count from. */
  key: FilterSetKey
  labelKey: string
  label: string
  Control: ComponentType<FilterControlProps>
  /** Rendered in this entity mode only; both modes when absent. */
  mode?: EntityMode
  /** Mobile: sits above the chevron, expanded on first paint. */
  primary?: boolean
  /** Suspends on i18n data and takes a width per layout. */
  dropdown?: boolean
}

/** Render order, shared by both layouts. */
const FILTERS: readonly DeckFilter[] = [
  {
    key: 'selectedSinners',
    labelKey: 'filters.sinner',
    label: 'Sinner',
    Control: SinnerControl,
    primary: true,
  },
  {
    key: 'selectedKeywords',
    labelKey: 'filters.keyword',
    label: 'Keyword',
    Control: KeywordControl,
    primary: true,
  },
  {
    key: 'selectedAttributes',
    labelKey: 'filters.skillAttribute',
    label: 'Skill Attribute',
    Control: SkillAttributeControl,
  },
  {
    key: 'selectedAtkTypes',
    labelKey: 'filters.attackType',
    label: 'Attack Type',
    Control: AttackTypeControl,
  },
  {
    key: 'selectedDefTypes',
    labelKey: 'filters.defenseType',
    label: 'Defense Type',
    Control: DefenseTypeControl,
    mode: 'identity',
  },
  {
    key: 'selectedEgoTypes',
    labelKey: 'filters.egoType',
    label: 'EGO Type',
    Control: EGOTypeControl,
    mode: 'ego',
  },
  {
    key: 'selectedRaritys',
    labelKey: 'filters.rank',
    label: 'Rarity',
    Control: RarityControl,
    mode: 'identity',
  },
  {
    key: 'selectedSeasons',
    labelKey: 'filters.season',
    label: 'Season',
    Control: SeasonControl,
    dropdown: true,
  },
  {
    key: 'selectedUnitKeywords',
    labelKey: 'filters.unitKeywords',
    label: 'Unit Keywords',
    Control: UnitKeywordControl,
    mode: 'identity',
    dropdown: true,
  },
  {
    key: 'selectedBattleKeywords',
    labelKey: 'filters.additionalKeyword',
    label: 'Additional Keywords',
    Control: BattleKeywordControl,
    dropdown: true,
  },
]

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
    filterState.selectedBattleKeywords,
  )
  const hasSearch = filterState.searchQuery.length > 0
  const hasActiveFilters = activeFilterCount > 0
  const canReset = hasActiveFilters || hasSearch

  const isIdentityMode = filterState.entityMode === 'identity'

  const setField: SetFilterField = (key) => (value) => {
    setFilterState((prev) => ({ ...prev, [key]: value }))
  }

  const applicableFilters = FILTERS.filter(
    (filter) => filter.mode === undefined || filter.mode === filterState.entityMode,
  )

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
        canReset &&
          'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive',
      )}
    >
      {t('filters.resetAll', 'Reset All')}
      {hasActiveFilters && (
        <span className="ml-1 text-muted-foreground">({activeFilterCount})</span>
      )}
    </Button>
  )

  const searchPlaceholder = isIdentityMode
    ? t('planner:deckBuilder.identitySearchPlaceholder')
    : t('planner:deckBuilder.egoSearchPlaceholder')

  const searchBar = (
    <SearchBar
      searchQuery={filterState.searchQuery}
      onSearchChange={setField('searchQuery')}
      placeholder={searchPlaceholder}
    />
  )

  if (isDesktop) {
    return (
      <div className={cn('flex flex-wrap items-center gap-2', 'rounded-lg border bg-card p-2')}>
        <EntityToggle mode={filterState.entityMode} onModeChange={setField('entityMode')} />
        {applicableFilters.map((filter) =>
          filter.dropdown ? (
            <Suspense key={filter.key} fallback={<Skeleton className="h-10 w-48 rounded-md" />}>
              <filter.Control state={filterState} setField={setField} className="w-48" />
            </Suspense>
          ) : (
            <div key={filter.key} className={FILTER_BOX}>
              <filter.Control state={filterState} setField={setField} />
            </div>
          ),
        )}
        <SearchBar
          searchQuery={filterState.searchQuery}
          onSearchChange={setField('searchQuery')}
          placeholder={searchPlaceholder}
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
            <EntityToggle mode={filterState.entityMode} onModeChange={setField('entityMode')} />
          </div>

          {applicableFilters
            .filter((filter) => filter.primary === true || isExpanded)
            .map((filter) => (
              <FilterSection
                key={filter.key}
                title={t(filter.labelKey, filter.label)}
                defaultExpanded={filter.primary === true}
                activeCount={filterState[filter.key].size}
              >
                {filter.dropdown ? (
                  <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
                    <filter.Control state={filterState} setField={setField} />
                  </Suspense>
                ) : (
                  <filter.Control state={filterState} setField={setField} />
                )}
              </FilterSection>
            ))}

          <div>{searchBar}</div>

          <Button
            variant="outline"
            size="sm"
            onClick={handleResetAll}
            disabled={!canReset}
            className={cn(
              'w-full',
              canReset &&
                'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive',
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
        onClick={() => {
          setIsExpanded(!isExpanded)
        }}
        aria-expanded={isExpanded}
        aria-label={
          isExpanded
            ? t('filters.collapse', 'Collapse filters')
            : t('filters.expand', 'Expand filters')
        }
        className={cn(
          'absolute left-1/2 bottom-0 -translate-x-1/2 translate-y-1/2',
          'size-8 rounded-full',
          'bg-card border border-border',
          'flex items-center justify-center',
          'hover:bg-accent transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
        )}
      >
        <ChevronDown
          className={cn('size-4 transition-transform duration-200', isExpanded && 'rotate-180')}
        />
      </button>
    </div>
  )
}
