import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'
import { getSinnerFromId } from '@/lib/utils'

/**
 * Identity searchable dropdown with i18n names.
 * Maps identity spec + name list into SearchableMultiSelect options.
 * Appends sinner name to distinguish identities (e.g., "LCB Sinner - Yi Sang").
 */
export function IdentitySearchDropdown({
  selectedIdentities,
  onSelectionChange,
}: {
  selectedIdentities: Set<string>
  onSelectionChange: (ids: Set<string>) => void
}) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const { spec, i18n: identityNames } = useIdentityListData()

  const options = useMemo(() =>
    Object.keys(spec).map((id) => {
      const sinnerKey = getSinnerFromId(id)
      const sinnerName = t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
      const identityName = (identityNames[id] ?? id).replace(/\n/g, ' ')
      return {
        value: id,
        label: `${identityName} - ${sinnerName}`,
      }
    }),
    [spec, identityNames, t]
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
