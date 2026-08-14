import { useTranslation } from 'react-i18next'
import { CompactIconFilter } from '@/shared/filter'
import { getKeywordIconPath } from '@/shared/assets'
import { KEYWORD_ORDER } from '@/shared/gameData'

interface EGOGiftKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function EGOGiftKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: EGOGiftKeywordFilterProps) {
  const { t } = useTranslation()

  // Filter out "None" - it needs special button treatment
  const filterKeywords = KEYWORD_ORDER.filter((k) => k !== 'None')

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
      options={filterKeywords}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getKeywordIconPath}
      layout="bar"
      onClearAll={() => {
        onSelectionChange(new Set())
      }}
    >
      <button
        onClick={handleNoneClick}
        role="checkbox"
        aria-checked={isNoneSelected}
        aria-label={`${t('filter.common', 'None')} filter`}
        data-selected={isNoneSelected}
        className="selectable shrink-0 size-8 rounded-md border border-border"
        title={t('filter.common', 'None')}
      >
        <svg viewBox="0 0 100 100" className="w-full h-full">
          <rect x="30" y="30" width="40" height="40" fill="currentColor" />
        </svg>
      </button>
    </CompactIconFilter>
  )
}
