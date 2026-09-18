import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { buildNameOptions } from './searchDropdownOptions'

export function EgoGiftSearchDropdown({
  selected,
  onSelectionChange,
  ids,
  names,
}: {
  selected: Set<string>
  onSelectionChange: (gifts: Set<string>) => void
  ids: string[]
  names: Record<string, string>
}) {
  const { t } = useTranslation('database')

  const options = buildNameOptions(ids, names)

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.egoGift', 'EGO Gift')}
      searchPlaceholder={t('filters.searchEgoGift', 'Search EGO Gifts...')}
    />
  )
}
