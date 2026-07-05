import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { buildNameOptions, type SearchListDataHook } from './searchDropdownOptions'

/**
 * EGO Gift searchable dropdown with i18n names.
 * Shared by AbEventPage and ThemePackPage filter panes.
 * Gifts are not sinner-owned: no sinner suffix on labels (unlike the
 * identity/EGO search dropdowns).
 */
export function EgoGiftSearchDropdown({
  selectedEgoGifts,
  onSelectionChange,
  useListData,
}: {
  selectedEgoGifts: Set<string>
  onSelectionChange: (gifts: Set<string>) => void
  useListData: SearchListDataHook
}) {
  const { t } = useTranslation('database')
  const { spec, i18n: giftNames } = useListData()

  const options = useMemo(() => buildNameOptions(Object.keys(spec), giftNames), [spec, giftNames])

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedEgoGifts}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.egoGift', 'EGO Gift')}
      searchPlaceholder={t('filters.searchEgoGift', 'Search EGO Gifts...')}
    />
  )
}
