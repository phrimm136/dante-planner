import { EntitySearchDropdown } from '@/shared/filter'
import { useEGOListSpec, useEGOListI18n } from '../hooks/useEGOListData'
import { typedEntries } from '@/lib/utils'

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
  const spec = useEGOListSpec()
  const i18n = useEGOListI18n()

  return (
    <EntitySearchDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      ids={typedEntries(spec).map(([id]) => id)}
      names={i18n}
      placeholderKey={placeholderKey}
    />
  )
}
