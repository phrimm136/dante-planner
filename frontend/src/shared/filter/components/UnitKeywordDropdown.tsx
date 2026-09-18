import { useTranslation } from 'react-i18next'

import { ASSOCIATIONS } from '@/shared/gameData'
import { useFilterI18nData } from '../hooks/useFilterI18nData'
import { applyStrikethrough, extractLeadingColor, stripRichTextTags } from '@/shared/gameText'
import { SearchableMultiSelect } from './SearchableMultiSelect'

interface UnitKeywordDropdownProps {
  selected: Set<string>
  onSelectionChange: (unitKeywords: Set<string>) => void
  counts?: Record<string, number>
  className?: string
}

function formatUnitKeywordLabel(label: string) {
  const { color, text } = extractLeadingColor(label)
  if (color === undefined) return applyStrikethrough(text)

  return <span style={{ color }}>{applyStrikethrough(text)}</span>
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
      label: stripRichTextTags(rawLabel),
      renderLabel: formatUnitKeywordLabel(rawLabel),
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
