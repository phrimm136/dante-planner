interface SingleSelectOption<T> {
  value: T
  label: string
}

interface SingleSelectRowProps<T> {
  label?: string
  options: readonly SingleSelectOption<T>[]
  selected: T
  onSelect: (value: T) => void
}

export function SingleSelectRow<T extends string>({
  label,
  options,
  selected,
  onSelect,
}: SingleSelectRowProps<T>) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const isSelected = option.value === selected
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={isSelected}
            data-selected={isSelected}
            onClick={() => {
              onSelect(option.value)
            }}
            className="selectable h-10 rounded-md border border-border px-4 text-sm"
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
