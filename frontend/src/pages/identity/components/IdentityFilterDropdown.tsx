import { EntitySearchDropdown } from '@/shared/filter'
import { useIdentityListSpec, useIdentityListI18n } from '../hooks/useIdentityListData'
import { typedEntries } from '@/lib/utils'

interface IdentityFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (ids: Set<string>) => void
  placeholderKey: string
}

export function IdentityFilterDropdown({
  selected,
  onSelectionChange,
  placeholderKey,
}: IdentityFilterDropdownProps) {
  const spec = useIdentityListSpec()
  const i18n = useIdentityListI18n()

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
