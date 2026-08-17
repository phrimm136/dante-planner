import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { buildNameOptions } from './searchDropdownOptions'

/**
 * EGO Gift searchable dropdown with i18n names.
 * Gifts are not sinner-owned: no sinner suffix on labels (unlike the
 * identity/EGO search dropdowns).
 *
 * The egoGift slice fetches ids/names and renders this inside its own Suspense
 * boundary, keeping `shared/filter` free of any `@/pages/*` import (sink rule).
 */
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
