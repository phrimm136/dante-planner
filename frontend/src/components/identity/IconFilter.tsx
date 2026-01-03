import { Button } from '@/components/ui/button'

interface IconFilterProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  clearLabel?: string
}

export function IconFilter({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  clearLabel = 'Clear all filters',
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
    <div className="bg-card border border-border rounded-md p-2 h-14 flex items-center gap-2">
      {/* Clear All Button */}
      <Button
        variant="outline"
        size="icon-sm"
        onClick={clearAll}
        className="shrink-0"
        title={clearLabel}
      >
        <span className="text-xs">×</span>
      </Button>

      {/* Scrollable Icons */}
      <div className="flex gap-2 overflow-x-auto">
        {options.map((option) => {
          const isSelected = selectedOptions.has(option)
          return (
            <button
              key={option}
              onClick={() => { toggleOption(option); }}
              className="selectable shrink-0 w-8 h-8 rounded-md border-2 border-border bg-button"
              data-selected={isSelected}
              title={option}
            >
              <img
                src={getIconPath(option)}
                alt={option}
                className="w-full h-full object-contain"
              />
            </button>
          )
        })}
      </div>
    </div>
  )
}
