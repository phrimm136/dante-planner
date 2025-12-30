import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { SEASONS } from '@/lib/constants'
import type { SeasonsI18n } from '@/types/FilterTypes'

interface SeasonDropdownProps {
  selectedSeasons: Set<number>
  onSelectionChange: (seasons: Set<number>) => void
  seasonsI18n: SeasonsI18n
}

/**
 * Multi-select dropdown for season filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Follows IconFilter.tsx container styling with dropdown
 */
export function SeasonDropdown({
  selectedSeasons,
  onSelectionChange,
  seasonsI18n,
}: SeasonDropdownProps) {
  const { t } = useTranslation()

  const toggleSeason = (season: number) => {
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
          className="w-full justify-between"
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
        {SEASONS.map((season) => {
          const label = seasonsI18n[`${season}` as keyof SeasonsI18n] || `Season ${season}`
          return (
            <DropdownMenuCheckboxItem
              key={season}
              checked={selectedSeasons.has(season)}
              onCheckedChange={() => { toggleSeason(season); }}
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
