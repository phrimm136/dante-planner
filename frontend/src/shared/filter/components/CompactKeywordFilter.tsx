import { getBattleKeywordIconPath } from '@/shared/assets'
import { CompactIconFilter } from './CompactIconFilter'
import { STATUS_EFFECTS } from '@/shared/gameData'

interface CompactKeywordFilterProps {
  selected: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

/**
 * Compact keyword icon filter for filter sidebar
 * 7 status effects displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps CompactIconFilter like KeywordFilter wraps IconFilter
 */
export function CompactKeywordFilter({ selected, onSelectionChange }: CompactKeywordFilterProps) {
  return (
    <CompactIconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getBattleKeywordIconPath}
    />
  )
}
