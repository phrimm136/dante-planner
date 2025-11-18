import { getStatusEffectIconPath } from '@/lib/identityUtils'
import { IconFilter } from '@/components/common/IconFilter'
import { STATUS_EFFECTS } from '@/lib/globalConstants'

interface EGOKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function EGOKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: EGOKeywordFilterProps) {
  const getIconPath = (keyword: string) => getStatusEffectIconPath(`[${keyword}]`)

  return (
    <IconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      clearLabel="Clear all filters"
    />
  )
}
