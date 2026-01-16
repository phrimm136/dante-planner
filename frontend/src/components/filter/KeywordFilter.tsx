import { memo } from 'react'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/filter/IconFilter'
import { STATUS_EFFECTS } from '@/lib/constants'

interface KeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

// Helper to compare Sets by content
function areSetsEqual(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false
  for (const item of a) {
    if (!b.has(item)) return false
  }
  return true
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
}, (prev, next) => {
  return areSetsEqual(prev.selectedKeywords, next.selectedKeywords)
  // onSelectionChange excluded - callback identity changes but behavior is same
})
