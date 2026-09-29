import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'

import { useFilterStore } from '@/components/hooks/filterStore'
import {
  FilterPageLayout,
  FilterSectionList,
  SearchBar,
  calculateActiveFilterCount,
  filterSection,
  type FilterSectionEntry,
} from '@/shared/filter'
import { useAssistantListSpec } from '../hooks/useAssistantListData'
import { useEnemyListSpec } from '../hooks/useEnemyListData'
import { useSinnerEventListSpec } from '../hooks/useSinnerEventListData'
import { toAssistantEntity } from '../lib/assistant/assistantEntity'
import { modeItems, type BattleCardItem } from '../lib/battleListing'
import type { BattleCase } from '../lib/battleVocabulary'
import { toEnemyEntity } from '../lib/enemy/enemyEntity'
import { collectEnemyFacetKeys } from '../lib/enemy/enemyFacetOptions'
import { toSinnerEventEntity } from '../lib/sinnerEvent/sinnerEventEntity'
import { battleFilterStore } from '../stores/battleFilterStore'
import { battleModeStore, selectBattleMode } from '../stores/battleModeStore'
import { BattleList } from './BattleList'
import {
  ChapterFilter,
  EnemyThemePackFilter,
  EventFilter,
  HiddenBattleFilter,
  LuxcavationFilter,
  RailwayLineFilter,
  TypeFilter,
} from './EnemyFilterControls'

export function BattlePanel() {
  const { t } = useTranslation('database')
  const mode = useStore(battleModeStore, selectBattleMode)
  const enemies = useEnemyListSpec()
  const assistants = useAssistantListSpec()
  const sinnerEvents = useSinnerEventListSpec()
  const items = modeItems<BattleCardItem>(
    [
      ...Object.entries(enemies).map(([id, entry]) => ({
        family: 'enemy' as const,
        entity: toEnemyEntity(id, entry),
      })),
      ...Object.entries(assistants).map(([id, entry]) => ({
        family: 'assistant' as const,
        entity: toAssistantEntity(id, entry),
      })),
      ...Object.entries(sinnerEvents).map(([id, entry]) => ({
        family: 'sinnerEvent' as const,
        entity: toSinnerEventEntity(id, entry),
      })),
    ],
    mode,
  )
  const keys = collectEnemyFacetKeys(items.map((item) => item.entity))

  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(battleFilterStore)

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
      key: 'selectedTypes',
      titleKey: 'battle.facet.type',
      Component: TypeFilter,
      selected: filters.selectedTypes,
      onSelectionChange: setters.selectedTypes,
    }),
    ...drillDowns(mode),
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
      <BattleList items={items} mode={mode} store={store} />
    </FilterPageLayout>
  )
}
