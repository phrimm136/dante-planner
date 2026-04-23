import { useState, useMemo, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, ChevronDown } from 'lucide-react'

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import type { ReactNode } from 'react'

interface SearchableMultiSelectOption {
  value: string
  /** Plain text label used for search matching */
  label: string
  /** Custom render content - falls back to label when omitted */
  renderLabel?: ReactNode
  /** Count displayed on the right side of the option */
  count?: number
}

interface SearchableMultiSelectProps {
  options: SearchableMultiSelectOption[]
  selectedValues: Set<string>
  onSelectionChange: (values: Set<string>) => void
  placeholder: string
  searchPlaceholder: string
  emptyMessage?: string
  className?: string
  /** Sort options alphabetically by label using locale collation (default: true) */
  sortByLabel?: boolean
}

const BATCH_SIZE = 50

/**
 * Multi-select dropdown with search, built on shadcn Command + Popover.
 * Sorts options by current locale and progressively renders large lists.
 */
export function SearchableMultiSelect({
  options,
  selectedValues,
  onSelectionChange,
  placeholder,
  searchPlaceholder,
  emptyMessage = 'No results found.',
  className,
  sortByLabel = true,
}: SearchableMultiSelectProps) {
  const [open, setOpen] = useState(false)
  const { i18n } = useTranslation()
  const [displayCount, setDisplayCount] = useState(BATCH_SIZE)

  const sortedOptions = useMemo(() => {
    if (!open) return options
    if (!sortByLabel) return options
    const collator = new Intl.Collator(i18n.language, { sensitivity: 'base' })
    return [...options].sort((a, b) => collator.compare(a.label, b.label))
  }, [open, options, i18n.language, sortByLabel])

  // Reset progressive count when popover opens
  useEffect(() => {
    if (open) {
      setDisplayCount(BATCH_SIZE)
    }
  }, [open])

  // Progressive rendering
  useEffect(() => {
    if (open && displayCount < sortedOptions.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + BATCH_SIZE, sortedOptions.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [open, displayCount, sortedOptions.length])

  const toggleValue = (value: string) => {
    const newSelection = new Set(selectedValues)
    if (newSelection.has(value)) {
      newSelection.delete(value)
    } else {
      newSelection.add(value)
    }
    onSelectionChange(newSelection)
  }

  const selectedCount = selectedValues.size

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          data-selected={selectedCount > 0}
          className={cn('selectable w-full justify-between', className)}
        >
          <span>
            {placeholder}
            {selectedCount > 0 && (
              <span className="ml-2 text-muted-foreground">({selectedCount})</span>
            )}
          </span>
          <ChevronDown className="size-4 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
      >
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
            <CommandGroup>
              {sortedOptions.slice(0, displayCount).map((option) => {
                const isSelected = selectedValues.has(option.value)
                return (
                  <CommandItem
                    key={option.value}
                    value={option.label}
                    onSelect={() => { toggleValue(option.value); }}
                  >
                    <Check
                      className={cn(
                        'mr-2 size-4',
                        isSelected ? 'opacity-100' : 'opacity-0'
                      )}
                    />
                    <span className="flex items-center justify-between w-full">
                      <span>{option.renderLabel ?? option.label}</span>
                      {option.count != null && (
                        <span className="text-xs text-muted-foreground ml-2">
                          {option.count}
                        </span>
                      )}
                    </span>
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
