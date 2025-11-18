import { getStatusEffectIconPath } from '@/lib/identityUtils'
import { IconFilter } from '@/components/common/IconFilter'
import { STATUS_EFFECTS } from '@/lib/globalConstants'

interface IdentityKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function IdentityKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: IdentityKeywordFilterProps) {
  const getIconPath = (keyword: string) => getStatusEffectIconPath(keyword)

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
