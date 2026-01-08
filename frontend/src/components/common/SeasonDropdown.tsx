import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { SEASONS, type Season } from '@/lib/constants'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'

interface SeasonDropdownProps {
  selectedSeasons: Set<Season>
  onSelectionChange: (seasons: Set<Season>) => void
}

// Prevent dropdown from closing when selecting items
const preventClose = (e: Event) => { e.preventDefault() }

/**
 * Individual season checkbox item
 * Extracted as separate component to help React Compiler optimize
 */
function SeasonItem({
  season,
  label,
  isSelected,
  onToggle,
}: {
  season: Season
  label: string
  isSelected: boolean
  onToggle: (season: Season) => void
}) {
  return (
    <DropdownMenuCheckboxItem
      checked={isSelected}
      onCheckedChange={() => onToggle(season)}
      onSelect={preventClose}
    >
      {label}
    </DropdownMenuCheckboxItem>
  )
}

/**
 * Multi-select dropdown for season filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 *
 * Pattern: Follows IconFilter.tsx container styling with dropdown
 *
 * Performance: Extracted SeasonItem component helps React Compiler optimize
 * rendering. Compiler auto-memoizes discrete components better than inline closures.
 */
export function SeasonDropdown({
  selectedSeasons,
  onSelectionChange,
}: SeasonDropdownProps) {
  const { t } = useTranslation()
  const { seasonsI18n } = useFilterI18nData()

  const toggleSeason = (season: Season) => {
    const newSelection = new Set(selectedSeasons)
    if (newSelection.has(season)) {
      newSelection.delete(season)
    } else {
      newSelection.add(season)
    }
    onSelectionChange(newSelection)
  }

  const selectedCount = selectedSeasons.size

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-selected={selectedCount > 0}
          className="selectable w-full justify-between"
        >
          <span>
            {t('filters.season', 'Season')}
            {selectedCount > 0 && (
              <span className="ml-2 text-muted-foreground">({selectedCount})</span>
            )}
          </span>
          <ChevronDown className="size-4 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)] max-h-[300px] overflow-y-auto">
        {SEASONS.map((season) => (
          <SeasonItem
            key={season}
            season={season}
            label={seasonsI18n[`${season}`] || `Season ${season}`}
            isSelected={selectedSeasons.has(season)}
            onToggle={toggleSeason}
          />
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
