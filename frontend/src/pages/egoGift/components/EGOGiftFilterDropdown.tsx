import { EgoGiftSearchDropdown } from '@/shared/filter'
import { useEGOGiftListData } from '../hooks/useEGOGiftListData'

interface EGOGiftFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (gifts: Set<string>) => void
}

/**
 * EGO Gift binding of the shared gift search dropdown. Suspends while the gift
 * spec + name list load, so render it inside a Suspense boundary.
 */
export function EGOGiftFilterDropdown({ selected, onSelectionChange }: EGOGiftFilterDropdownProps) {
  const { spec, i18n } = useEGOGiftListData()

  return (
    <EgoGiftSearchDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      ids={Object.keys(spec)}
      names={i18n}
    />
  )
}
