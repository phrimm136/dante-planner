import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
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
  const getLabel = (keyword: string) => getKeywordDisplayName(keyword)

  return (
    <IconFilter
      options={filterKeywords}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      getLabel={getLabel}
      clearLabel="Clear all filters"
    />
  )
}
