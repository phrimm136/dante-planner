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
import type { AssociationsI18n } from '@/types/FilterTypes'

interface AssociationDropdownProps {
  selectedAssociations: Set<string>
  onSelectionChange: (associations: Set<string>) => void
  associationsI18n: AssociationsI18n
}

/**
 * Multi-select dropdown for association filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Follows IconFilter.tsx container styling with dropdown
 */
export function AssociationDropdown({
  selectedAssociations,
  onSelectionChange,
  associationsI18n,
}: AssociationDropdownProps) {
  const { t } = useTranslation()

  const toggleAssociation = (association: string) => {
    const newSelection = new Set(selectedAssociations)
    if (newSelection.has(association)) {
      newSelection.delete(association)
    } else {
      newSelection.add(association)
    }
    onSelectionChange(newSelection)
  }

  const selectedCount = selectedAssociations.size

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-selected={selectedCount > 0}
          className="selectable w-full justify-between"
        >
          <span>
            {t('filters.association', 'Association')}
            {selectedCount > 0 && (
              <span className="ml-2 text-muted-foreground">({selectedCount})</span>
            )}
          </span>
          <ChevronDown className="size-4 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)] max-h-[300px] overflow-y-auto">
        {ASSOCIATIONS.map((association) => {
          const label = associationsI18n[association] || association
          return (
            <DropdownMenuCheckboxItem
              key={association}
              checked={selectedAssociations.has(association)}
              onCheckedChange={() => { toggleAssociation(association); }}
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
