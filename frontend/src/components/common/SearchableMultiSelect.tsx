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

interface SearchableMultiSelectProps {
  options: { value: string; label: string }[]
  selectedValues: Set<string>
  onSelectionChange: (values: Set<string>) => void
  placeholder: string
  searchPlaceholder: string
  emptyMessage?: string
  className?: string
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
}: SearchableMultiSelectProps) {
  const [open, setOpen] = useState(false)
  const { i18n } = useTranslation()
  const [displayCount, setDisplayCount] = useState(BATCH_SIZE)

  const sortedOptions = useMemo(() => {
    const collator = new Intl.Collator(i18n.language, { sensitivity: 'base' })
    return [...options].sort((a, b) => collator.compare(a.label, b.label))
  }, [options, i18n.language])

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
                    {option.label}
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
