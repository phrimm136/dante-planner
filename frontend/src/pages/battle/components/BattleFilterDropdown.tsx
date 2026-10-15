import { useTranslation } from 'react-i18next'
import { SearchableMultiSelect } from '@/shared/filter'
import { useBattleCardNames } from '../hooks/useBattleCardNames'
import type { BattleFamily } from '../lib/battleVocabulary'

interface BattleFilterDropdownProps {
  family: BattleFamily
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  placeholderKey: string
}

export function BattleFilterDropdown({
  family,
  selected,
  onSelectionChange,
  placeholderKey,
}: BattleFilterDropdownProps) {
  const { t } = useTranslation('database')
  const names = useBattleCardNames(family)

  const options = Object.entries(names).map(([id, label]) => ({ value: id, label }))

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t(placeholderKey)}
      searchPlaceholder={t('keyword.searchPlaceholder')}
    />
  )
}
