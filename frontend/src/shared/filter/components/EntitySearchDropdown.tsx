import { useTranslation } from 'react-i18next'
import { getSinnerFromId } from '@/shared/gameData'
import type { SinnerScopedId } from '@/shared/gameData'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { buildSinnerSuffixedOptions } from './searchDropdownOptions'

export function EntitySearchDropdown({
  selected,
  onSelectionChange,
  ids,
  names,
  placeholderKey,
}: {
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  ids: SinnerScopedId[]
  names: Record<string, string>
  placeholderKey: string
}) {
  const { t } = useTranslation(['database', 'sinnerNames'])

  const options = buildSinnerSuffixedOptions(ids, names, (id) => {
    const sinnerKey = getSinnerFromId(id)
    return t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
  })

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t(placeholderKey, { ns: 'database' })}
      searchPlaceholder={t('keyword.searchPlaceholder', { ns: 'database' })}
    />
  )
}
