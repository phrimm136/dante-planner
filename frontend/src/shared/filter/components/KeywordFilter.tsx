import { getBattleKeywordIconPath } from '@/shared/assets'
import { IconFilter } from './IconFilter'
import { STATUS_EFFECTS } from '@/shared/gameData'

interface KeywordFilterProps {
  selected: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

/**
 * Keyword icon filter for filter sidebar
 * 7 status effects displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps IconFilter like KeywordFilter wraps IconFilter
 */
export function KeywordFilter({ selected, onSelectionChange }: KeywordFilterProps) {
  return (
    <IconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getBattleKeywordIconPath}
    />
  )
}
