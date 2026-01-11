import { useTranslation } from 'react-i18next'

import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { KEYWORD_ORDER } from '@/lib/constants'

interface CompactEGOGiftKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

/**
 * Compact EGO Gift keyword filter for filter sidebar
 * - 7 status effects + 3 attack types + "None" button
 * - Uses 6-column grid (2 rows)
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Combines CompactKeywordFilter structure with EGOGiftKeywordFilter logic
 * - Uses KEYWORD_ORDER (includes status effects, attack types, and None)
 * - "None" is rendered as a text button via children prop
 */
export function CompactEGOGiftKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: CompactEGOGiftKeywordFilterProps) {
  const { t } = useTranslation()

  // Filter out "None" - it needs special text button treatment
  const iconKeywords = KEYWORD_ORDER.filter((k) => k !== 'None')

  const handleNoneClick = () => {
    const newKeywords = new Set(selectedKeywords)
    if (newKeywords.has('None')) {
      newKeywords.delete('None')
    } else {
      newKeywords.add('None')
    }
    onSelectionChange(newKeywords)
  }

  const isNoneSelected = selectedKeywords.has('None')

  return (
    <CompactIconFilter
      options={iconKeywords}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getKeywordIconPath}
      columns={6}
    >
      <button
        onClick={handleNoneClick}
        role="checkbox"
        aria-checked={isNoneSelected}
        aria-label={`${t('filter.common', 'None')} filter`}
        data-selected={isNoneSelected}
        className="selectable rounded-md border border-border p-0.5 w-full aspect-square"
        title={t('filter.common', 'None')}
      >
        <svg viewBox="0 0 100 100" className="w-full h-full">
          <rect x="30" y="30" width="40" height="40" fill="currentColor" />
        </svg>
      </button>
    </CompactIconFilter>
  )
}
