import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/common/IconFilter'
import { KEYWORD_ORDER } from '@/lib/constants'
import { Button } from '@/components/ui/button'
import { useTranslation } from 'react-i18next'

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

  const getIconPath = (keyword: string) => getStatusEffectIconPath(keyword)

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
        clearLabel="Clear all filters"
      >
      <Button
        variant="outline"
        size="sm"
        onClick={handleNoneClick}
        className={`transition-all ${
          isNoneSelected
            ? 'border-primary bg-primary/10'
            : ''
        }`}
      >
        {t('filter.common', 'None')}
      </Button>
      </IconFilter>
    </div>
  )
}
