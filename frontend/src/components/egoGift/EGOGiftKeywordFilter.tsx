import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/common/IconFilter'
import { KEYWORD_ORDER } from '@/lib/constants'

interface EGOGiftKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function EGOGiftKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: EGOGiftKeywordFilterProps) {
  // Filter out "Common" from the keyword options
  const filterKeywords = KEYWORD_ORDER.filter((k) => k !== 'Common')

  const getIconPath = (keyword: string) => getStatusEffectIconPath(keyword)

  // TODO: Add common filter
  return (
    <IconFilter
      options={filterKeywords}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      clearLabel="Clear all filters"
    />
  )
}
