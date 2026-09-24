import { useTranslation } from 'react-i18next'

import { useFilterStore } from '@/components/hooks/filterStore'
import {
  FilterPageLayout,
  FilterSectionList,
  SearchBar,
  calculateActiveFilterCount,
  filterSection,
  type FilterSectionEntry,
} from '@/shared/filter'
import { useEnemyListSpec } from '../hooks/useEnemyListData'
import { BATTLE_CASES, type BattleCase } from '../lib/battleVocabulary'
import { collectEnemyFacetKeys } from '../lib/enemy/enemyFacetOptions'
import { enemyFilterStore, setEnemyCases } from '../stores/enemyFilterStore'
import {
  CaseFilter,
  ChapterFilter,
  EnemyThemePackFilter,
  EventFilter,
  HiddenBattleFilter,
  LuxcavationFilter,
  RailwayLineFilter,
} from './EnemyFilterControls'
import { EnemyList } from './EnemyList'

export function EnemyPanel() {
  const { t } = useTranslation('database')
  const spec = useEnemyListSpec()
  const keys = collectEnemyFacetKeys(spec)

  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(enemyFilterStore)

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const drillDowns = (battleCase: BattleCase): FilterSectionEntry[] => {
    switch (battleCase) {
      case 'story':
        return [
          filterSection({
            key: 'selectedChapters',
            titleKey: 'battle.facet.chapter',
            suspense: true,
            Component: ChapterFilter,
            selected: filters.selectedChapters,
            onSelectionChange: setters.selectedChapters,
            props: { keys: keys.chapters },
          }),
        ]
      case 'luxcavation':
        return [
          filterSection({
            key: 'selectedLuxcavations',
            titleKey: 'battle.facet.luxcavation',
            suspense: true,
            Component: LuxcavationFilter,
            selected: filters.selectedLuxcavations,
            onSelectionChange: setters.selectedLuxcavations,
            props: { keys: keys.luxcavations },
          }),
        ]
      case 'mirrorDungeon':
        return [
          filterSection({
            key: 'selectedThemePacks',
            titleKey: 'battle.facet.themePack',
            suspense: true,
            Component: EnemyThemePackFilter,
            selected: filters.selectedThemePacks,
            onSelectionChange: setters.selectedThemePacks,
            props: { keys: keys.themePacks },
          }),
          filterSection({
            key: 'selectedHidden',
            titleKey: 'battle.facet.hidden',
            Component: HiddenBattleFilter,
            selected: filters.selectedHidden,
            onSelectionChange: setters.selectedHidden,
          }),
          filterSection({
            key: 'selectedEvents',
            titleKey: 'battle.facet.event',
            suspense: true,
            Component: EventFilter,
            selected: filters.selectedEvents,
            onSelectionChange: setters.selectedEvents,
            props: { keys: keys.events },
          }),
        ]
      case 'refractionRailway':
        return [
          filterSection({
            key: 'selectedRailwayLines',
            titleKey: 'battle.facet.railwayLine',
            suspense: true,
            Component: RailwayLineFilter,
            selected: filters.selectedRailwayLines,
            onSelectionChange: setters.selectedRailwayLines,
            props: { keys: keys.railwayLines },
          }),
        ]
      case 'refractral':
        return []
    }
  }

  const sections = [
    filterSection({
      key: 'selectedCases',
      titleKey: 'battle.facet.case',
      Component: CaseFilter,
      selected: filters.selectedCases,
      onSelectionChange: setEnemyCases,
    }),
    ...BATTLE_CASES.filter((battleCase) => filters.selectedCases.has(battleCase)).flatMap(
      drillDowns,
    ),
  ]

  return (
    <FilterPageLayout
      primaryFilters={<FilterSectionList sections={sections} />}
      activeFilterCount={activeFilterCount}
      onResetAll={resetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('pages.battle.searchBar')}
        />
      }
    >
      <EnemyList spec={spec} store={store} />
    </FilterPageLayout>
  )
}
