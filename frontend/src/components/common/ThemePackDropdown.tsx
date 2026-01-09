import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { useThemePackListData } from '@/hooks/useThemePackListData'

interface ThemePackDropdownProps {
  selectedThemePacks: Set<string>
  onThemePacksChange: (themePacks: Set<string>) => void
}

/**
 * Multi-select dropdown for theme pack filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Follows SeasonDropdown.tsx structure
 */
export function ThemePackDropdown({
  selectedThemePacks,
  onThemePacksChange,
}: ThemePackDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { themePackList, themePackI18n } = useThemePackListData()

  const toggleThemePack = (themePackId: string) => {
    const newSelection = new Set(selectedThemePacks)
    if (newSelection.has(themePackId)) {
      newSelection.delete(themePackId)
    } else {
      newSelection.add(themePackId)
    }
    onThemePacksChange(newSelection)
  }

  const selectedCount = selectedThemePacks.size
  const themePackIds = Object.keys(themePackList)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-selected={selectedCount > 0}
          className="selectable w-full justify-between"
        >
          <span>
            {t('filters.themePack', 'Theme Pack')}
            {selectedCount > 0 && (
              <span className="ml-2 text-muted-foreground">({selectedCount})</span>
            )}
          </span>
          <ChevronDown className="size-4 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)] max-h-[300px] overflow-y-auto">
        {themePackIds.map((themePackId) => {
          const i18nEntry = themePackI18n[themePackId]
          const label = i18nEntry?.name ?? `Theme Pack ${themePackId}`
          return (
            <DropdownMenuCheckboxItem
              key={themePackId}
              checked={selectedThemePacks.has(themePackId)}
              onCheckedChange={() => { toggleThemePack(themePackId); }}
              onSelect={(e) => { e.preventDefault(); }}
            >
              {label}
            </DropdownMenuCheckboxItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
