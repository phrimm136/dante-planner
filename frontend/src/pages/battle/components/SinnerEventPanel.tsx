import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'

import { useFilterStore } from '@/components/hooks/filterStore'
import { FilterPageLayout, SearchBar, calculateActiveFilterCount } from '@/shared/filter'
import { useSinnerEventListSpec } from '../hooks/useSinnerEventListData'
import { modeItems } from '../lib/battleListing'
import { toSinnerEventEntity } from '../lib/sinnerEvent/sinnerEventEntity'
import { battleTypeStore, selectBattleMode } from '../stores/battleTypeStore'
import { sinnerEventFilterStore } from '../stores/sinnerEventFilterStore'
import { SinnerEventList } from './SinnerEventList'

export function SinnerEventPanel() {
  const { t } = useTranslation('database')
  const spec = useSinnerEventListSpec()
  const mode = useStore(battleTypeStore, selectBattleMode)
  const items = modeItems(
    Object.entries(spec).map(([id, entry]) => toSinnerEventEntity(id, entry)),
    mode,
  )
  const {
    values: filters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(sinnerEventFilterStore)

  return (
    <FilterPageLayout
      activeFilterCount={calculateActiveFilterCount(...Object.values(filters))}
      onResetAll={resetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('pages.battle.searchBar')}
        />
      }
    >
      <SinnerEventList items={items} mode={mode} store={store} />
    </FilterPageLayout>
  )
}
