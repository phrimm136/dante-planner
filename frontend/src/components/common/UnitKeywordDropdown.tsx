import { useState, useEffect } from 'react'
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

// Prevent dropdown from closing when selecting items
const preventClose = (e: Event) => { e.preventDefault() }

/**
 * Parse Unity rich text tags (color, strikethrough) to React elements.
 * Same pattern as TraitsI18n.tsx:parseUnityRichText + renderTrait.
 */
function formatUnitKeywordLabel(label: string) {
  const colorMatch = label.match(/<color=([^>]+)>/)
  if (!colorMatch) return label

  const color = colorMatch[1]
  let text = label.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  const hasStrikethrough = text.includes('<s>')
  if (hasStrikethrough) {
    text = text.replace(/<s>/g, '').replace(/<\/s>/g, '')
  }

  const content = hasStrikethrough ? <s>{text}</s> : text
  return <span style={{ color }}>{content}</span>
}

/**
 * Individual unit keyword checkbox item
 * Extracted as separate component to help React Compiler optimize
 */
function UnitKeywordItem({
  unitKeyword,
  label,
  isSelected,
  onToggle,
}: {
  unitKeyword: string
  label: string
  isSelected: boolean
  onToggle: (keyword: string) => void
}) {
  return (
    <DropdownMenuCheckboxItem
      checked={isSelected}
      onCheckedChange={() => onToggle(unitKeyword)}
      onSelect={preventClose}
    >
      {formatUnitKeywordLabel(label)}
    </DropdownMenuCheckboxItem>
  )
}

/**
 * Multi-select dropdown for unit keyword (association/affiliation) filtering
 * Uses DropdownMenuCheckboxItem for multi-selection
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 *
 * Pattern: Follows SeasonDropdown / IconFilter.tsx container styling with dropdown
 */
export function UnitKeywordDropdown({
  selectedUnitKeywords,
  onSelectionChange,
}: UnitKeywordDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { unitKeywordsI18n } = useFilterI18nData()
  const [open, setOpen] = useState(false)
  const [displayCount, setDisplayCount] = useState(0)

  // Sort associations alphabetically by localized display name
  const sortedAssociations = [...ASSOCIATIONS].sort((a, b) => {
    const labelA = (unitKeywordsI18n[a] || a).replace(/<[^>]+>/g, '')
    const labelB = (unitKeywordsI18n[b] || b).replace(/<[^>]+>/g, '')
    return labelA.localeCompare(labelB)
  })

  useEffect(() => {
    if (!open) {
      setDisplayCount(0)
      return
    }
    setDisplayCount(15)
  }, [open])

  useEffect(() => {
    if (displayCount > 0 && displayCount < ASSOCIATIONS.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 20, ASSOCIATIONS.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount])

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
    <DropdownMenu open={open} onOpenChange={setOpen}>
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
        {sortedAssociations.slice(0, displayCount).map((unitKeyword) => (
          <UnitKeywordItem
            key={unitKeyword}
            unitKeyword={unitKeyword}
            label={unitKeywordsI18n[unitKeyword] || unitKeyword}
            isSelected={selectedUnitKeywords.has(unitKeyword)}
            onToggle={toggleUnitKeyword}
          />
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
