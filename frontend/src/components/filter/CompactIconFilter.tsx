import { getKeywordDisplayName } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface CompactIconFilterProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  /** Icon path getter - if undefined, renders text labels instead of icons */
  getIconPath?: (option: string) => string
  /** Label getter for text mode (defaults to getKeywordDisplayName) */
  getLabel?: (option: string) => string
  /** Size variant: 'sm' for 24px icons, 'md' for 32px icons */
  size?: 'sm' | 'md'
  /** Fixed column count for icons (uses CSS grid). If not set, uses flex-wrap. */
  columns?: number
  /** Use flex layout with fixed height and auto width for icons (for varying-width images like rank) */
  flexIcons?: boolean
  children?: ReactNode
}

/**
 * Compact filter for filter sidebar - supports both icon and text modes
 *
 * Modes:
 * - Icon mode (getIconPath provided): Renders icon buttons
 * - Text mode (no getIconPath): Renders text label buttons
 *
 * Layout:
 * - Grid mode (columns prop): Items stretch to fill grid cells
 * - Flex mode (no columns): Items in flex-wrap with fixed sizes
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
  getLabel,
  size = 'sm',
  columns,
  flexIcons = false,
  children,
}: CompactIconFilterProps) {
  // Determine mode: icon vs text
  const isTextMode = !getIconPath
  const resolveLabel = getLabel ?? getKeywordDisplayName
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
  const buttonSize = size === 'sm' ? 'size-10 lg:size-8' : 'size-12 lg:size-10'

  // When using grid columns, buttons stretch to fill cells (auto-sized)
  // When using flex-wrap, buttons use fixed sizes
  const useGridStretch = Boolean(columns)

  // Flex layout with fixed-size buttons (grid mode removed - caused unwanted gaps)
  if (useGridStretch) {
    return (
      <div className="flex gap-1 flex-wrap">
        {options.map((option) => {
          const isSelected = selectedOptions.has(option)
          const label = resolveLabel(option)
          return (
            <button
              key={option}
              onClick={() => {
                toggleOption(option)
              }}
              role="checkbox"
              aria-checked={isSelected}
              aria-label={`${label} filter`}
              data-selected={isSelected}
              className={cn(
                'selectable rounded-md border border-border p-0.5',
                flexIcons ? 'h-10 lg:h-8 w-auto' : cn('aspect-square', buttonSize)
              )}
              title={label}
            >
              {isTextMode ? (
                <svg viewBox="0 0 100 100" className="w-full h-full">
                  <text
                    x="50"
                    y="50"
                    dominantBaseline="central"
                    textAnchor="middle"
                    fontSize={label.length > 3 ? 24 : 40}
                    fill="currentColor"
                  >
                    {label}
                  </text>
                </svg>
              ) : (
                <img
                  src={getIconPath!(option)}
                  alt={label}
                  className={
                    flexIcons
                      ? 'h-full w-auto'
                      : 'object-contain mx-auto w-8 h-8 lg:w-6 lg:h-6'
                  }
                />
              )}
            </button>
          )
        })}
        {children}
      </div>
    )
  }

  // Flex layout: fixed size items (or flexIcons mode)
  return (
    <div className="flex gap-1 flex-wrap justify-start">
      {options.map((option) => {
        const isSelected = selectedOptions.has(option)
        const label = resolveLabel(option)
        return (
          <button
            key={option}
            onClick={() => {
              toggleOption(option)
            }}
            role="checkbox"
            aria-checked={isSelected}
            aria-label={`${label} filter`}
            data-selected={isSelected}
            className={cn(
              'selectable rounded-md border border-border p-0.5',
              flexIcons ? 'h-10 lg:h-8 w-auto' : cn('aspect-square', buttonSize)
            )}
            title={label}
          >
            {isTextMode ? (
              <svg viewBox="0 0 100 100" className="w-full h-full">
                <text
                  x="50"
                  y="50"
                  dominantBaseline="central"
                  textAnchor="middle"
                  fontSize={label.length > 3 ? 24 : label.length > 2 ? 32 : 40}
                  fill="currentColor"
                >
                  {label}
                </text>
              </svg>
            ) : (
              <img
                src={getIconPath!(option)}
                alt={label}
                className={flexIcons ? 'h-full w-auto' : cn('object-contain mx-auto', iconSize)}
              />
            )}
          </button>
        )
      })}
      {children}
    </div>
  )
}
