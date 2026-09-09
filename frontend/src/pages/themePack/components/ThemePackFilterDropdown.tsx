import { ThemePackDropdown } from '@/shared/filter'
import { useThemePackListSpec, useThemePackListI18n } from '../hooks/useThemePackListData'

interface ThemePackFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (themePacks: Set<string>) => void
}

/**
 * Theme pack binding of the shared theme-pack dropdown. Suspends while the pack
 * list + names load, so render it inside a Suspense boundary.
 */
export function ThemePackFilterDropdown({
  selected,
  onSelectionChange,
}: ThemePackFilterDropdownProps) {
  const spec = useThemePackListSpec()
  const i18n = useThemePackListI18n()

  return (
    <ThemePackDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      packs={spec}
      names={i18n}
    />
  )
}
