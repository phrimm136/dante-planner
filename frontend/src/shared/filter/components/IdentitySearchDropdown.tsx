import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { getSinnerFromId } from '@/shared/gameData'
import { buildSinnerSuffixedOptions, type SearchListDataHook } from './searchDropdownOptions'

/**
 * Identity searchable dropdown with i18n names.
 * Maps identity spec + name list into SearchableMultiSelect options.
 * Appends sinner name to distinguish identities (e.g., "LCB Sinner - Yi Sang").
 */
export function IdentitySearchDropdown({
  selectedIdentities,
  onSelectionChange,
  useListData,
}: {
  selectedIdentities: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  useListData: SearchListDataHook
}) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const { spec, i18n: identityNames } = useListData()

  const options = useMemo(
    () =>
      buildSinnerSuffixedOptions(Object.keys(spec), identityNames, (id) => {
        const sinnerKey = getSinnerFromId(id)
        return t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
      }),
    [spec, identityNames, t],
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedIdentities}
      onSelectionChange={onSelectionChange}
      placeholder={t('keyword.filterIdentity', { ns: 'database' })}
      searchPlaceholder={t('keyword.searchPlaceholder', { ns: 'database' })}
    />
  )
}
