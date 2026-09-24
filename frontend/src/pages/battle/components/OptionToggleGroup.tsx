import { cn } from '@/lib/utils'

export interface ToggleOption<T> {
  value: T
  label: string
}

interface OptionToggleGroupProps<T> {
  options: readonly ToggleOption<T>[]
  selected: Set<T>
  onSelectionChange: (next: Set<T>) => void
  className?: string
}

export function OptionToggleGroup<T extends string | number>({
  options,
  selected,
  onSelectionChange,
  className,
}: OptionToggleGroupProps<T>) {
  const toggle = (value: T) => {
    const next = new Set(selected)
    if (next.has(value)) {
      next.delete(value)
    } else {
      next.add(value)
    }
    onSelectionChange(next)
  }

  return (
    <div className={cn('flex flex-wrap gap-1', className)}>
      {options.map((option) => {
        const isSelected = selected.has(option.value)
        return (
          <button
            key={String(option.value)}
            type="button"
            aria-pressed={isSelected}
            data-selected={isSelected}
            onClick={() => {
              toggle(option.value)
            }}
            className="selectable h-10 rounded-md border border-border px-2 text-xs lg:h-8"
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
