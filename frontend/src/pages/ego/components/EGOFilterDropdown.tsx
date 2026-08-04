import { EntitySearchDropdown } from '@/shared/filter'
import { useEGOListData } from '../hooks/useEGOListData'

interface EGOFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  /** `database` namespace key for the collapsed-state placeholder */
  placeholderKey: string
}

/**
 * EGO binding of the shared entity search dropdown. Suspends while the EGO spec
 * + name list load, so render it inside a Suspense boundary.
 */
export function EGOFilterDropdown({
  selected,
  onSelectionChange,
  placeholderKey,
}: EGOFilterDropdownProps) {
  const { spec, i18n } = useEGOListData()

  return (
    <EntitySearchDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      ids={Object.keys(spec)}
      names={i18n}
      placeholderKey={placeholderKey}
    />
  )
}
