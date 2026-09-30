import { useTranslation } from 'react-i18next'

import { ASSOCIATIONS } from '@/shared/gameData'
import { useFilterI18nData } from '../hooks/useFilterI18nData'
import { applyTextStyles, tmpPlainText } from '@/shared/gameText'
import { SearchableMultiSelect } from './SearchableMultiSelect'

interface UnitKeywordDropdownProps {
  selected: Set<string>
  onSelectionChange: (unitKeywords: Set<string>) => void
  counts?: Record<string, number>
  className?: string
}

export function UnitKeywordDropdown({
  selected,
  onSelectionChange,
  counts,
  className,
}: UnitKeywordDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { unitKeywordsI18n } = useFilterI18nData()

  const options = ASSOCIATIONS.map((unitKeyword) => {
    const rawLabel = unitKeywordsI18n[unitKeyword] || unitKeyword
    return {
      value: unitKeyword,
      label: tmpPlainText(rawLabel),
      renderLabel: applyTextStyles(rawLabel),
      count: counts?.[unitKeyword],
    }
  })

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.unitKeywords', 'Unit Keywords')}
      searchPlaceholder={t('filters.searchUnitKeywords', 'Search Unit Keywords...')}
      className={className}
    />
  )
}
