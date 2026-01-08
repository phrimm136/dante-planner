import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { ASSOCIATIONS } from '@/lib/constants'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'

interface UnitKeywordDropdownProps {
  selectedUnitKeywords: Set<string>
  onSelectionChange: (unitKeywords: Set<string>) => void
}

/**
 * Multi-select dropdown for unit keyword (association/affiliation) filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 *
 * Pattern: Follows IconFilter.tsx container styling with dropdown
 */
export function UnitKeywordDropdown({
  selectedUnitKeywords,
  onSelectionChange,
}: UnitKeywordDropdownProps) {
  const { t } = useTranslation()
  const { unitKeywordsI18n } = useFilterI18nData()

  const toggleUnitKeyword = (unitKeyword: string) => {
    const newSelection = new Set(selectedUnitKeywords)
    if (newSelection.has(unitKeyword)) {
      newSelection.delete(unitKeyword)
    } else {
      newSelection.add(unitKeyword)
    }
    onSelectionChange(newSelection)
  }

  const selectedCount = selectedUnitKeywords.size

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-selected={selectedCount > 0}
          className="selectable w-full justify-between"
        >
          <span>
            {t('filters.unitKeywords', 'Unit Keywords')}
            {selectedCount > 0 && (
              <span className="ml-2 text-muted-foreground">({selectedCount})</span>
            )}
          </span>
          <ChevronDown className="size-4 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)] max-h-[300px] overflow-y-auto">
        {ASSOCIATIONS.map((unitKeyword) => {
          const label = unitKeywordsI18n[unitKeyword] || unitKeyword
          return (
            <DropdownMenuCheckboxItem
              key={unitKeyword}
              checked={selectedUnitKeywords.has(unitKeyword)}
              onCheckedChange={() => { toggleUnitKeyword(unitKeyword); }}
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
