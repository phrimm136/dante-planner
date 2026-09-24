import { useTranslation } from 'react-i18next'

import { useFilterStore } from '@/components/hooks/filterStore'
import { FilterPageLayout, SearchBar, calculateActiveFilterCount } from '@/shared/filter'
import { useAssistantListSpec } from '../hooks/useAssistantListData'
import { assistantFilterStore } from '../stores/assistantFilterStore'
import { AssistantList } from './AssistantList'

export function AssistantPanel() {
  const { t } = useTranslation('database')
  const spec = useAssistantListSpec()
  const {
    values: filters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(assistantFilterStore)

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
      <AssistantList spec={spec} store={store} />
    </FilterPageLayout>
  )
}
