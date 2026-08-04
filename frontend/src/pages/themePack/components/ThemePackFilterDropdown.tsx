import { ThemePackDropdown } from '@/shared/filter'
import { useThemePackListData } from '../hooks/useThemePackListData'

interface ThemePackFilterDropdownProps {
  selected: Set<string>
  onThemePacksChange: (themePacks: Set<string>) => void
}

/**
 * Theme pack binding of the shared theme-pack dropdown. Suspends while the pack
 * list + names load, so render it inside a Suspense boundary.
 */
export function ThemePackFilterDropdown({
  selected,
  onThemePacksChange,
}: ThemePackFilterDropdownProps) {
  const { spec, i18n } = useThemePackListData()

  return (
    <ThemePackDropdown
      selected={selected}
      onThemePacksChange={onThemePacksChange}
      packs={spec}
      names={i18n}
    />
  )
}
