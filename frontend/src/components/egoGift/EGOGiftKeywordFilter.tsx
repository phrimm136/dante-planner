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

  // Filter out "Keywordless" from the keyword options for IconFilter
  const filterKeywords = KEYWORD_ORDER.filter((k) => k !== 'Keywordless')

  const getIconPath = (keyword: string) => getStatusEffectIconPath(keyword)

  const handleKeywordlessClick = () => {
    const newKeywords = new Set(selectedKeywords)
    if (newKeywords.has('Keywordless')) {
      newKeywords.delete('Keywordless')
    } else {
      newKeywords.add('Keywordless')
    }
    onSelectionChange(newKeywords)
  }

  const isKeywordlessSelected = selectedKeywords.has('Keywordless')
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
        onClick={handleKeywordlessClick}
        className={`transition-all ${
          isKeywordlessSelected
            ? 'border-primary bg-primary/10'
            : ''
        }`}
      >
        {t('filter.common', 'Keywordless')}
      </Button>
      </IconFilter>
    </div>
  )
}
