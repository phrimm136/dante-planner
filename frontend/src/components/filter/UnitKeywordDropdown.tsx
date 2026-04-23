import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { ASSOCIATIONS } from '@/lib/constants'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'

interface UnitKeywordDropdownProps {
  selectedUnitKeywords: Set<string>
  onSelectionChange: (unitKeywords: Set<string>) => void
  /** Entity count per unit keyword for display */
  counts?: Record<string, number>
  className?: string
}

/**
 * Parse Unity rich text tags (color, strikethrough) to React elements.
 * Same pattern as TraitsI18n.tsx:parseUnityRichText + renderTrait.
 */
function formatUnitKeywordLabel(label: string) {
  const colorMatch = label.match(/<color=([^>]+)>/)
  if (!colorMatch) return label

  const color = colorMatch[1]
  let text = label.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  const hasStrikethrough = text.includes('<s>')
  if (hasStrikethrough) {
    text = text.replace(/<s>/g, '').replace(/<\/s>/g, '')
  }

  const content = hasStrikethrough ? <s>{text}</s> : text
  return <span style={{ color }}>{content}</span>
}

/** Strip Unity rich text tags for plain text search */
function stripTags(label: string): string {
  return label.replace(/<[^>]+>/g, '')
}

/**
 * Multi-select searchable dropdown for unit keyword (association/affiliation) filtering.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 */
export function UnitKeywordDropdown({
  selectedUnitKeywords,
  onSelectionChange,
  counts,
  className,
}: UnitKeywordDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { unitKeywordsI18n } = useFilterI18nData()

  const options = useMemo(
    () =>
      ASSOCIATIONS.map((unitKeyword) => {
        const rawLabel = unitKeywordsI18n[unitKeyword] || unitKeyword
        return {
          value: unitKeyword,
          label: stripTags(rawLabel),
          renderLabel: formatUnitKeywordLabel(rawLabel),
          count: counts?.[unitKeyword],
        }
      }),
    [unitKeywordsI18n, counts]
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedUnitKeywords}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.unitKeywords', 'Unit Keywords')}
      searchPlaceholder={t('filters.searchUnitKeywords', 'Search Unit Keywords...')}
      className={className}
    />
  )
}
