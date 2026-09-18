import { EgoGiftSearchDropdown } from '@/shared/filter'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '../hooks/useEGOGiftListData'

interface EGOGiftFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (gifts: Set<string>) => void
}

export function EGOGiftFilterDropdown({ selected, onSelectionChange }: EGOGiftFilterDropdownProps) {
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  return (
    <EgoGiftSearchDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      ids={Object.keys(spec)}
      names={i18n}
    />
  )
}
