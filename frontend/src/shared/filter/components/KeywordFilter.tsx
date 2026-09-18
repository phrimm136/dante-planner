import { getBattleKeywordIconPath } from '@/shared/assets'
import { IconFilter } from './IconFilter'
import { STATUS_EFFECTS } from '@/shared/gameData'

interface KeywordFilterProps {
  selected: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

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
