import { useTranslation } from 'react-i18next'
import { IconFilter } from '@/components/filter/IconFilter'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { KEYWORD_ORDER } from '@/lib/constants'

interface EGOGiftKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function EGOGiftKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: EGOGiftKeywordFilterProps) {
  const { t } = useTranslation()

  // Filter out "None" from the keyword options for IconFilter
  const filterKeywords = KEYWORD_ORDER.filter((k) => k !== 'None')

  const getIconPath = (keyword: string) => getKeywordIconPath(keyword)

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
    <div className="flex gap-2 items-center h-14">
      <IconFilter
        options={filterKeywords}
        selectedOptions={selectedKeywords}
        onSelectionChange={onSelectionChange}
        getIconPath={getIconPath}
      >
      <button
        onClick={handleNoneClick}
        role="checkbox"
        aria-checked={isNoneSelected}
        aria-label={`${t('filter.common', 'None')} filter`}
        data-selected={isNoneSelected}
        className="selectable shrink-0 w-8 h-8 rounded-md border border-border"
        title={t('filter.common', 'None')}
      >
        <svg viewBox="0 0 100 100" className="w-full h-full">
          <rect x="30" y="30" width="40" height="40" fill="currentColor" />
        </svg>
      </button>
      </IconFilter>
    </div>
  )
}
