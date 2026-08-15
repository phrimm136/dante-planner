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
import { FilterSectionList, filterSection } from '@/shared/filter'
import { SinnerFilter } from '@/shared/filter'
import { KeywordFilter } from '@/shared/filter'
import { SkillAttributeFilter } from '@/shared/filter'
import { AttackTypeFilter } from '@/shared/filter'
import { DefenseTypeFilter } from '@/shared/filter'
import { RarityFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { UnitKeywordDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { buildFacetCounts } from './lib/identityFacetCounts'

/**
 * Card grid section.
 */
function IdentityCardGrid({
  spec,
  store,
}: {
  spec: z.infer<typeof IdentitySpecListSchema>
  store: FilterStore<IdentityFacetState>
}) {
  // Build IdentityListItem array from spec directly (no transformation needed)
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

  const { seasonCounts, unitKeywordCounts } = buildFacetCounts(spec)

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
  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedSinners',
      titleKey: 'filters.sinner',
      titleFallback: 'Sinner',
      Component: SinnerFilter,
      selected: filters.selectedSinners,
      onSelectionChange: setters.selectedSinners,
    }),
    filterSection({
      key: 'selectedKeywords',
      titleKey: 'filters.keyword',
      titleFallback: 'Keyword',
      Component: KeywordFilter,
      selected: filters.selectedKeywords,
      onSelectionChange: setters.selectedKeywords,
    }),
  ]

  // Secondary filters (shown when mobile expanded): Skill Attribute, Attack Type, Rarity, Season, Unit Keywords
  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedAttributes',
      titleKey: 'filters.skillAttribute',
      titleFallback: 'Skill Attribute',
      Component: SkillAttributeFilter,
      selected: filters.selectedAttributes,
      onSelectionChange: setters.selectedAttributes,
    }),
    filterSection({
      key: 'selectedAtkTypes',
      titleKey: 'filters.attackType',
      titleFallback: 'Attack Type',
      Component: AttackTypeFilter,
      selected: filters.selectedAtkTypes,
      onSelectionChange: setters.selectedAtkTypes,
    }),
    filterSection({
      key: 'selectedDefTypes',
      titleKey: 'filters.defenseType',
      titleFallback: 'Defense Type',
      Component: DefenseTypeFilter,
      selected: filters.selectedDefTypes,
      onSelectionChange: setters.selectedDefTypes,
    }),
    filterSection({
      key: 'selectedRaritys',
      titleKey: 'filters.rank',
      titleFallback: 'Rarity',
      Component: RarityFilter,
      selected: filters.selectedRaritys,
      onSelectionChange: setters.selectedRaritys,
    }),
    filterSection({
      key: 'selectedSeasons',
      titleKey: 'filters.season',
      titleFallback: 'Season',
      suspense: true,
      Component: SeasonDropdown,
      selected: filters.selectedSeasons,
      onSelectionChange: setters.selectedSeasons,
      props: { counts: seasonCounts },
    }),
    filterSection({
      key: 'selectedUnitKeywords',
      titleKey: 'filters.unitKeywords',
      titleFallback: 'Unit Keywords',
      suspense: true,
      Component: UnitKeywordDropdown,
      selected: filters.selectedUnitKeywords,
      onSelectionChange: setters.selectedUnitKeywords,
      props: { counts: unitKeywordCounts },
    }),
    filterSection({
      key: 'selectedBattleKeywords',
      titleKey: 'filters.additionalKeyword',
      titleFallback: 'Additional Keywords',
      suspense: true,
      Component: BattleKeywordDropdown,
      selected: filters.selectedBattleKeywords,
      onSelectionChange: setters.selectedBattleKeywords,
      props: { entityType: 'identity' as const },
    }),
  ]

  return (
    <FilterPageLayout
      primaryFilters={<FilterSectionList sections={PRIMARY_FILTERS} />}
      secondaryFilters={<FilterSectionList sections={SECONDARY_FILTERS} />}
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
 * - IdentityList: name lookups suspend at the card name, not the grid
 */
export default function IdentityPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton preset="identity" />}>
      <IdentityPageShell />
    </EntityListPage>
  )
}
