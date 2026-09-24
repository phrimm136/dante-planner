import { ThemePackDropdown } from '@/shared/filter'
import { useThemePackListI18n } from '@/shared/themePack'
import { useThemePackListSpec } from '../hooks/useThemePackListData'

interface ThemePackFilterDropdownProps {
  selected: Set<string>
  onSelectionChange: (themePacks: Set<string>) => void
}

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
