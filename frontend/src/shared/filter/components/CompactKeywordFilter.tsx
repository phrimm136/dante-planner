import { getBattleKeywordIconPath } from '@/shared/assets'
import { CompactIconFilter } from './CompactIconFilter'
import { STATUS_EFFECTS } from '@/shared/gameData'

interface CompactKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

/**
 * Compact keyword icon filter for filter sidebar
 * 7 status effects displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps CompactIconFilter like KeywordFilter wraps IconFilter
 * Uses columns prop for grid stretch sizing (same as CompactSinnerFilter)
 */
export function CompactKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: CompactKeywordFilterProps) {
  return (
    <CompactIconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getBattleKeywordIconPath}
      columns={7}
    />
  )
}
