import { getKeywordDisplayName } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface CompactIconFilterProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  /** Size variant: 'sm' for 24px icons, 'md' for 32px icons */
  size?: 'sm' | 'md'
  /** Fixed column count for icons (uses CSS grid). If not set, uses flex-wrap. */
  columns?: number
  children?: ReactNode
}

/**
 * Compact icon filter for filter sidebar
 *
 * Layout:
 * - Grid mode (columns prop): Icons stretch to fill grid cells
 * - Flex mode (no columns): Icons in flex-wrap with fixed sizes
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Follows IconFilter.tsx logic
 * Used in: FilterSidebar for compact filter display
 */
export function CompactIconFilter({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  size = 'sm',
  columns,
  children,
}: CompactIconFilterProps) {
  const toggleOption = (option: string) => {
    const newSelection = new Set(selectedOptions)
    if (newSelection.has(option)) {
      newSelection.delete(option)
    } else {
      newSelection.add(option)
    }
    onSelectionChange(newSelection)
  }

  // Responsive sizes: larger on mobile, smaller on desktop
  // Mobile-first: default is larger, lg breakpoint is smaller
  const iconSize = size === 'sm' ? 'size-8 lg:size-6' : 'size-10 lg:size-8'
  const buttonSize = size === 'sm' ? 'size-9 lg:size-7' : 'size-11 lg:size-9'

  // When using grid columns, buttons stretch to fill cells (auto-sized)
  // When using flex-wrap, buttons use fixed sizes
  const useGridStretch = Boolean(columns)

  // Grid layout: icons stretch to fill cells
  if (useGridStretch) {
    return (
      <div
        className="grid gap-1"
        style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
      >
        {options.map((option) => {
          const isSelected = selectedOptions.has(option)
          const label = getKeywordDisplayName(option)
          return (
            <button
              key={option}
              onClick={() => {
                toggleOption(option)
              }}
              role="checkbox"
              aria-checked={isSelected}
              aria-label={`${label} filter`}
              className={cn(
                'rounded-md border-2 transition-all p-0.5',
                'w-full aspect-square', // Stretch to fill grid cell
                isSelected
                  ? 'border-primary bg-primary/10'
                  : 'border-border bg-card hover:border-primary/50'
              )}
              title={label}
            >
              <img
                src={getIconPath(option)}
                alt={label}
                className="w-full h-full object-contain"
              />
            </button>
          )
        })}
        {children}
      </div>
    )
  }

  // Flex layout: fixed size icons
  return (
    <div className="flex gap-1 flex-wrap justify-start">
      {options.map((option) => {
        const isSelected = selectedOptions.has(option)
        const label = getKeywordDisplayName(option)
        return (
          <button
            key={option}
            onClick={() => {
              toggleOption(option)
            }}
            role="checkbox"
            aria-checked={isSelected}
            aria-label={`${label} filter`}
            className={cn(
              'rounded-md border-2 transition-all p-0.5',
              buttonSize, // Fixed size for flex-wrap
              isSelected
                ? 'border-primary bg-primary/10'
                : 'border-border bg-card hover:border-primary/50'
            )}
            title={label}
          >
            <img
              src={getIconPath(option)}
              alt={label}
              className={cn('object-contain mx-auto', iconSize)}
            />
          </button>
        )
      })}
      {children}
    </div>
  )
}
