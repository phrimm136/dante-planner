import { memo } from 'react'
import { getKeywordIconPath } from '@/shared/assets'
import { areSetsEqual } from '@/lib/setUtils'
import { IconFilter } from './IconFilter'
import { STATUS_EFFECTS } from '@/shared/gameData'

interface KeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export const KeywordFilter = memo(
  function KeywordFilter({ selectedKeywords, onSelectionChange }: KeywordFilterProps) {
    const getIconPath = (keyword: string) => getKeywordIconPath(keyword)

    return (
      <IconFilter
        options={STATUS_EFFECTS}
        selectedOptions={selectedKeywords}
        onSelectionChange={onSelectionChange}
        getIconPath={getIconPath}
      />
    )
  },
  (prev, next) => {
    return areSetsEqual(prev.selectedKeywords, next.selectedKeywords)
    // onSelectionChange excluded - callback identity changes but behavior is same
  },
)
