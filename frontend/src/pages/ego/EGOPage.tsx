import { useTranslation } from 'react-i18next'
import { useEGOListSpec, EGOList } from '@/pages/ego'
import type { EGOListItem, EGOFacetState } from '@/pages/ego'
import type { EgoType } from '@/shared/gameData'
import { SearchBar } from '@/shared/filter'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import type { Season, SkillAttributeType, AtkType } from '@/shared/gameData'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useSetFilters } from '@/components/hooks/useSetFilters'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { SinnerFilter } from '@/shared/filter'
import { KeywordFilter } from '@/shared/filter'
import { AttackTypeFilter } from '@/shared/filter'
import { EGOTypeFilter } from '@/shared/filter'
import { SkillAttributeFilter } from '@/shared/filter'
import { SeasonDropdown } from '@/shared/filter'
import { BattleKeywordDropdown } from '@/shared/filter'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'

import type { z } from 'zod'
import type { EGOSpecListSchema } from '@/pages/ego'

/**
 * Card grid section.
 */
function EGOCardGrid({
  spec,
  store,
}: {
  spec: z.infer<typeof EGOSpecListSchema>
  store: FilterStore<EGOFacetState>
}) {
  // Build EGOListItem array from spec directly (no transformation needed)
  const egos: EGOListItem[] = Object.entries(spec).map(([id, specData]) => ({
    id,
    egoType: specData.egoType,
    skillKeywordList: specData.skillKeywordList,
    battleKeywordList: specData.battleKeywordList,
    attributeTypes: specData.attributeType,
    atkTypes: specData.atkType,
    updateDate: specData.updateDate,
    season: specData.season,
    maxThreadspin: specData.maxThreadspin,
  }))

  return <EGOList egos={egos} store={store} />
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function EGOPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useEGOListSpec()

  const seasonCounts = (() => {
    const sc: Record<string, number> = {}
    for (const entry of Object.values(spec)) {
      const key = String(entry.season)
      sc[key] = (sc[key] ?? 0) + 1
    }
    return sc
  })()

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
    selectedEGOTypes: new Set<EgoType>(),
    selectedSeasons: new Set<Season>(),
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

  // Secondary filters (shown when mobile expanded): Skill Attributes, Attack Types, EGO Types, Season
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
      key: 'selectedEGOTypes',
      titleKey: 'filters.egoType',
      titleFallback: 'EGO Type',
      Component: EGOTypeFilter,
      selected: filters.selectedEGOTypes as Set<string>,
      onSelectionChange: (types: Set<string>) => {
        setters.selectedEGOTypes(types as Set<EgoType>)
      },
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
      key: 'selectedBattleKeywords',
      titleKey: 'filters.additionalKeyword',
      titleFallback: 'Additional Keywords',
      suspense: true,
      Component: BattleKeywordDropdown,
      selected: filters.selectedBattleKeywords,
      onSelectionChange: setters.selectedBattleKeywords,
      props: { entityType: 'ego' as const },
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
          placeholder={t('pages.ego.searchBar')}
        />
      }
    >
      <EGOCardGrid spec={spec} store={store} />
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
 * - EGOList: name lookups suspend at the card name, not the grid
 */
export default function EGOPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton preset="ego" />}>
      <EGOPageShell />
    </EntityListPage>
  )
}
