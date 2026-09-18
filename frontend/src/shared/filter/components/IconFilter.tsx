import { useTranslation } from 'react-i18next'
import { getKeywordDisplayName } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface IconFilterProps<T extends string = string> {
  options: readonly T[]
  selectedOptions: Set<T>
  onSelectionChange: (options: Set<T>) => void
  getIconPath?: (option: T) => string
  getLabel?: (option: string) => string
  size?: 'sm' | 'md'
  flexIcons?: boolean
  layout?: 'wrap' | 'bar'
  onClearAll?: () => void
  children?: ReactNode
}

export function IconFilter<T extends string>({
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
}: IconFilterProps<T>) {
  const { t } = useTranslation()
  const clearAllLabel = t('filters.resetAll', 'Reset All')
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
