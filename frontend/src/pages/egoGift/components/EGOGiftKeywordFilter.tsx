import { useTranslation } from 'react-i18next'

import { IconFilter } from '@/shared/filter'
import { getKeywordIconPath } from '@/shared/assets'
import { KEYWORD_ORDER } from '@/shared/gameData'

interface EGOGiftKeywordFilterProps {
  selected: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
  layout?: 'wrap' | 'bar'
}

export function EGOGiftKeywordFilter({
  selected,
  onSelectionChange,
  layout = 'wrap',
}: EGOGiftKeywordFilterProps) {
  const { t } = useTranslation()

  const iconKeywords = KEYWORD_ORDER.filter((k) => k !== 'None')

  const handleNoneClick = () => {
    const newKeywords = new Set(selected)
    if (newKeywords.has('None')) {
      newKeywords.delete('None')
    } else {
      newKeywords.add('None')
    }
    onSelectionChange(newKeywords)
  }

  const isNoneSelected = selected.has('None')

  return (
    <IconFilter
      options={iconKeywords}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getKeywordIconPath}
      layout={layout}
      {...(layout === 'bar' && {
        onClearAll: () => {
          onSelectionChange(new Set())
        },
      })}
    >
      <button
        onClick={handleNoneClick}
        role="checkbox"
        aria-checked={isNoneSelected}
        aria-label={`${t('filter.common', 'None')} filter`}
        data-selected={isNoneSelected}
        className={
          layout === 'bar'
            ? 'selectable shrink-0 size-8 rounded-md border border-border'
            : 'selectable rounded-md border border-border p-0.5 size-10 lg:size-8'
        }
        title={t('filter.common', 'None')}
      >
        <svg viewBox="0 0 100 100" className="w-full h-full">
          <rect x="30" y="30" width="40" height="40" fill="currentColor" />
        </svg>
      </button>
    </IconFilter>
  )
}
