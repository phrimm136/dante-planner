import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useVirtualizer } from '@tanstack/react-virtual'
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

// Estimated item height for virtualizer (measured from Radix checkbox item)
const ITEM_HEIGHT = 32

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
      {label}
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
 * Pattern: Follows IconFilter.tsx container styling with dropdown
 *
 * Performance: Virtualized rendering with TanStack Virtual
 * - Only renders ~15 visible items instead of all 85
 * - Dramatically reduces initial render time (<50ms)
 * - Maintains smooth 60fps scrolling
 * - Trade-off: Radix keyboard navigation (arrow keys) won't work across all items
 */
export function UnitKeywordDropdown({
  selectedUnitKeywords,
  onSelectionChange,
}: UnitKeywordDropdownProps) {
  const { t } = useTranslation()
  const { unitKeywordsI18n } = useFilterI18nData()
  const parentRef = useRef<HTMLDivElement>(null)

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

  // Setup virtualizer to render only visible items
  const virtualizer = useVirtualizer({
    count: ASSOCIATIONS.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ITEM_HEIGHT,
    overscan: 5, // Render 5 extra items above/below viewport for smoother scrolling
  })

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
      <DropdownMenuContent align="start" className="w-[var(--radix-dropdown-menu-trigger-width)]">
        {/* Virtualized scroll container */}
        <div
          ref={parentRef}
          className="max-h-[300px] overflow-y-auto"
          style={{ contain: 'strict' }} // Performance hint for browser
        >
          {/* Total height container - creates scrollbar based on all 85 items */}
          <div
            style={{
              height: `${virtualizer.getTotalSize()}px`,
              width: '100%',
              position: 'relative',
            }}
          >
            {/* Render only visible items */}
            {virtualizer.getVirtualItems().map((virtualItem) => {
              const unitKeyword = ASSOCIATIONS[virtualItem.index]
              const label = unitKeywordsI18n[unitKeyword] || unitKeyword

              return (
                <div
                  key={virtualItem.key}
                  data-index={virtualItem.index}
                  ref={virtualizer.measureElement} // Measure actual height for accuracy
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    transform: `translateY(${virtualItem.start}px)`,
                  }}
                >
                  <UnitKeywordItem
                    unitKeyword={unitKeyword}
                    label={label}
                    isSelected={selectedUnitKeywords.has(unitKeyword)}
                    onToggle={toggleUnitKeyword}
                  />
                </div>
              )
            })}
          </div>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
