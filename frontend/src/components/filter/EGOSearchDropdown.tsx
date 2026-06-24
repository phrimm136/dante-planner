import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOListData } from '@/pages/ego'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'
import { getSinnerFromId } from '@/lib/utils'

/**
 * EGO searchable dropdown with i18n names.
 * Maps EGO spec + name list into SearchableMultiSelect options.
 * Appends sinner name to distinguish EGOs (e.g., "Crow's Eye View - Yi Sang").
 */
export function EGOSearchDropdown({
  selectedEgos,
  onSelectionChange,
}: {
  selectedEgos: Set<string>
  onSelectionChange: (ids: Set<string>) => void
}) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const { spec, i18n: egoNames } = useEGOListData()

  const options = useMemo(() =>
    Object.keys(spec).map((id) => {
      const sinnerKey = getSinnerFromId(id)
      const sinnerName = t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
      const egoName = (egoNames[id] ?? id).replace(/\n/g, ' ')
      return {
        value: id,
        label: `${egoName} - ${sinnerName}`,
      }
    }),
    [spec, egoNames, t]
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
