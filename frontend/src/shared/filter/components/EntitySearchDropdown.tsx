import { useTranslation } from 'react-i18next'
import { getSinnerFromId } from '@/shared/gameData'
import { SearchableMultiSelect } from './SearchableMultiSelect'
import { buildSinnerSuffixedOptions } from './searchDropdownOptions'

/**
 * Searchable dropdown for a sinner-owned entity (identities, EGOs) with i18n
 * names. Maps the supplied ids + name list into SearchableMultiSelect options,
 * appending the sinner name so same-named entries stay distinguishable
 * (e.g. "LCB Sinner - Yi Sang").
 *
 * The owning slice fetches ids/names and renders this inside its own Suspense
 * boundary, keeping `shared/filter` free of any `@/pages/*` import (sink rule).
 */
export function EntitySearchDropdown({
  selected,
  onSelectionChange,
  ids,
  names,
  placeholderKey,
}: {
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  ids: string[]
  names: Record<string, string>
  /** `database` namespace key for the collapsed-state placeholder */
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
