import { memo } from 'react'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/filter/IconFilter'
import { STATUS_EFFECTS } from '@/lib/constants'

interface KeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export const KeywordFilter = memo(function KeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: KeywordFilterProps) {
  const getIconPath = (keyword: string) => getKeywordIconPath(keyword)

  return (
    <IconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      clearLabel="Clear all filters"
    />
  )
})
