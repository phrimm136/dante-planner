import { getKeywordDisplayName } from '@/lib/utils'
import type { ReactNode } from 'react'

interface IconFilterProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  children?: ReactNode
}

export function IconFilter({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  children,
}: IconFilterProps) {
  const toggleOption = (option: string) => {
    const newSelection = new Set(selectedOptions)
    if (newSelection.has(option)) {
      newSelection.delete(option)
    } else {
      newSelection.add(option)
    }
    onSelectionChange(newSelection)
  }

  const clearAll = () => {
    onSelectionChange(new Set())
  }

  return (
    <div className="bg-card border border-border rounded-md h-14 flex items-center gap-2 px-2 min-w-0 overflow-x-auto">
      {/* Clear All Button */}
      <button
        onClick={clearAll}
        className="selectable shrink-0 w-8 h-8 flex items-center justify-center"
      >
        <span className="text-base">×</span>
      </button>

      {/* Scrollable Icons */}
      <div className="flex gap-2 shrink-0">
        {options.map((option) => {
          const isSelected = selectedOptions.has(option)
          const label = getKeywordDisplayName(option)
          return (
            <button
              key={option}
              onClick={() => { toggleOption(option); }}
              data-selected={isSelected}
              className="selectable shrink-0 w-8 h-8 rounded-md border border-border"
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
    </div>
  )
}
