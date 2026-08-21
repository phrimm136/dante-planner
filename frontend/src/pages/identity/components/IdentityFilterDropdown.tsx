import { EntitySearchDropdown } from '@/shared/filter'
import { useIdentityListData } from '../hooks/useIdentityListData'
import { typedEntries } from '@/lib/utils'

interface IdentityFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  /** `database` namespace key for the collapsed-state placeholder */
  placeholderKey: string
}

/**
 * Identity binding of the shared entity search dropdown. Suspends while the
 * identity spec + name list load, so render it inside a Suspense boundary.
 */
export function IdentityFilterDropdown({
  selected,
  onSelectionChange,
  placeholderKey,
}: IdentityFilterDropdownProps) {
  const { spec, i18n } = useIdentityListData()

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
