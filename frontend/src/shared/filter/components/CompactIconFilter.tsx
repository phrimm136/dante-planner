import { useTranslation } from 'react-i18next'
import { getKeywordDisplayName } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface CompactIconFilterProps<T extends string = string> {
  options: readonly T[]
  selectedOptions: Set<T>
  onSelectionChange: (options: Set<T>) => void
  /** Icon path getter - if undefined, renders text labels instead of icons */
  getIconPath?: (option: T) => string
  /** Label getter for text mode (defaults to getKeywordDisplayName) */
  getLabel?: (option: string) => string
  /** Size variant: 'sm' for 24px icons, 'md' for 32px icons */
  size?: 'sm' | 'md'
  /** Use flex layout with fixed height and auto width for icons (for varying-width images like rank) */
  flexIcons?: boolean
  /** 'wrap' fills the sidebar column; 'bar' is a single scrolling row on a card */
  layout?: 'wrap' | 'bar'
  /** Renders a clear-all control; omit where a parent "Reset All" owns the reset */
  onClearAll?: () => void
  children?: ReactNode
}

/**
 * Compact filter for filter sidebar - supports both icon and text modes
 *
 * Modes:
 * - Icon mode (getIconPath provided): Renders icon buttons
 * - Text mode (no getIconPath): Renders text label buttons
 *
 * Layouts:
 * - 'wrap' (default): flex-wrap with fixed-size items, for the sidebar column
 * - 'bar': a single scrolling row on a card
 *
 * Reset is the parent's "Reset All" unless onClearAll is passed.
 */
export function CompactIconFilter<T extends string>({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  getLabel,
  size = 'sm',
  flexIcons = false,
  layout = 'wrap',
  onClearAll,
  children,
}: CompactIconFilterProps<T>) {
  const { t } = useTranslation()
  const clearAllLabel = t('filters.resetAll', 'Reset All')
  // Determine mode: icon vs text
  const isTextMode = !getIconPath
  const resolveLabel = getLabel ?? getKeywordDisplayName
  const toggleOption = (option: T) => {
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

  const optionButtons = options.map((option) => {
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
          flexIcons ? 'h-10 lg:h-8 w-auto' : cn('aspect-square', buttonSize),
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
  })

  if (layout === 'bar') {
    return (
      <div className="bg-card border border-border rounded-md h-14 flex items-center gap-2 px-2 min-w-0 overflow-x-auto">
        {onClearAll && (
          <button
            onClick={onClearAll}
            aria-label={clearAllLabel}
            className="selectable shrink-0 size-8 flex items-center justify-center"
            title={clearAllLabel}
          >
            <span className="text-base">×</span>
          </button>
        )}
        <div className="flex gap-2 shrink-0">
          {optionButtons}
          {children}
        </div>
      </div>
    )
  }

  return (
    <div className="flex gap-1 flex-wrap justify-start">
      {onClearAll && (
        <button
          onClick={onClearAll}
          aria-label={clearAllLabel}
          className={cn(
            'selectable rounded-md border border-border p-0.5 flex items-center justify-center',
            flexIcons ? 'h-10 lg:h-8 w-auto' : cn('aspect-square', buttonSize),
          )}
          title={clearAllLabel}
        >
          <span className="text-base">×</span>
        </button>
      )}
      {optionButtons}
      {children}
    </div>
  )
}
