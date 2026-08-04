import { useTranslation } from 'react-i18next'

import { ASSOCIATIONS } from '@/shared/gameData'
import { useFilterI18nData } from '../hooks/useFilterI18nData'
import { applyStrikethrough, extractLeadingColor, stripRichTextTags } from '@/shared/gameText'
import { SearchableMultiSelect } from './SearchableMultiSelect'

interface UnitKeywordDropdownProps {
  selected: Set<string>
  onSelectionChange: (unitKeywords: Set<string>) => void
  /** Entity count per unit keyword for display */
  counts?: Record<string, number>
  className?: string
}

/** Parse Unity rich text tags (color, strikethrough) to React elements. */
function formatUnitKeywordLabel(label: string) {
  const { color, text } = extractLeadingColor(label)
  if (color === undefined) return applyStrikethrough(text)

  return <span style={{ color }}>{applyStrikethrough(text)}</span>
}

/**
 * Multi-select searchable dropdown for unit keyword (association/affiliation) filtering.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 */
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
