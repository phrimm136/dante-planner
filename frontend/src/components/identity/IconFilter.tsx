interface IconFilterProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
}

export function IconFilter({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
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
          return (
            <button
              key={option}
              onClick={() => { toggleOption(option); }}
              className="selectable shrink-0 w-8 h-8 rounded-md border border-border"
              data-selected={isSelected}
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
