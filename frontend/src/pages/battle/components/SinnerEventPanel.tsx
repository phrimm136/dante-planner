import { useTranslation } from 'react-i18next'

import { useFilterStore } from '@/components/hooks/filterStore'
import { FilterPageLayout, SearchBar, calculateActiveFilterCount } from '@/shared/filter'
import { useSinnerEventListSpec } from '../hooks/useSinnerEventListData'
import { sinnerEventFilterStore } from '../stores/sinnerEventFilterStore'
import { SinnerEventList } from './SinnerEventList'

export function SinnerEventPanel() {
  const { t } = useTranslation('database')
  const spec = useSinnerEventListSpec()
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
      <SinnerEventList spec={spec} store={store} />
    </FilterPageLayout>
  )
}
