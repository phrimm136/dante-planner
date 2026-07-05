import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { getSinnerFromId } from '@/shared/gameData'
import { buildSinnerSuffixedOptions, type SearchListDataHook } from './searchDropdownOptions'

/**
 * EGO searchable dropdown with i18n names.
 * Maps EGO spec + name list into SearchableMultiSelect options.
 * Appends sinner name to distinguish EGOs (e.g., "Crow's Eye View - Yi Sang").
 */
export function EGOSearchDropdown({
  selectedEgos,
  onSelectionChange,
  useListData,
}: {
  selectedEgos: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  useListData: SearchListDataHook
}) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const { spec, i18n: egoNames } = useListData()

  const options = useMemo(
    () =>
      buildSinnerSuffixedOptions(Object.keys(spec), egoNames, (id) => {
        const sinnerKey = getSinnerFromId(id)
        return t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
      }),
    [spec, egoNames, t],
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedEgos}
      onSelectionChange={onSelectionChange}
      placeholder={t('keyword.filterEgo', { ns: 'database' })}
      searchPlaceholder={t('keyword.searchPlaceholder', { ns: 'database' })}
    />
  )
}
