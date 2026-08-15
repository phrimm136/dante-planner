import { useState, useEffect, useId, useRef, memo } from 'react'
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
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
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
  count?: number | undefined
}

interface SearchableMultiSelectProps {
  options: SearchableMultiSelectOption[]
  selectedValues: Set<string>
  onSelectionChange: (values: Set<string>) => void
  placeholder: string
  searchPlaceholder: string
  emptyMessage?: string
  className?: string | undefined
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
  const listboxId = useId()

  // Read through a ref so the handler keeps one identity for the dropdown's lifetime.
  // Closing over `selectedValues` would give every option row a new callback on each
  // toggle, re-rendering all of them to change one checkmark.
  const latest = useRef({ selectedValues, onSelectionChange })
  useEffect(() => {
    latest.current = { selectedValues, onSelectionChange }
  })

  const [toggleValue] = useState(() => (value: string) => {
    const { selectedValues: current, onSelectionChange: notify } = latest.current
    const newSelection = new Set(current)
    if (newSelection.has(value)) {
      newSelection.delete(value)
    } else {
      newSelection.add(value)
    }
    notify(newSelection)
  })

  const selectedCount = selectedValues.size

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
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
        id={listboxId}
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
      >
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
            <CommandGroup>
              <OptionList
                options={options}
                selectedValues={selectedValues}
                sortByLabel={sortByLabel}
                onToggle={toggleValue}
              />
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

interface OptionListProps {
  options: SearchableMultiSelectOption[]
  selectedValues: Set<string>
  sortByLabel: boolean
  onToggle: (value: string) => void
}

/**
 * The open popover's option list, feeding itself in one batch per frame.
 *
 * Radix unmounts the popover content on close, so the progressive counter is
 * born fresh on every open and needs no reset. Sorting lives here for the same
 * reason: a closed dropdown never pays for it.
 */
function OptionList({ options, selectedValues, sortByLabel, onToggle }: OptionListProps) {
  const { i18n } = useTranslation()
  const [displayCount, setDisplayCount] = useState(BATCH_SIZE)

  const sortedOptions = (() => {
    if (!sortByLabel) return options
    const collator = new Intl.Collator(i18n.language, { sensitivity: 'base' })
    return [...options].sort((a, b) => collator.compare(a.label, b.label))
  })()

  useEffect(() => {
    if (displayCount >= sortedOptions.length) return

    const rafId = requestAnimationFrame(() => {
      setDisplayCount((prev) => Math.min(prev + BATCH_SIZE, sortedOptions.length))
    })
    return () => cancelAnimationFrame(rafId)
  }, [displayCount, sortedOptions.length])

  return sortedOptions
    .slice(0, displayCount)
    .map((option) => (
      <Option
        key={option.value}
        option={option}
        selected={selectedValues.has(option.value)}
        onToggle={onToggle}
      />
    ))
}

interface OptionProps {
  option: SearchableMultiSelectOption
  selected: boolean
  onToggle: (value: string) => void
}

/**
 * One option row.
 *
 * Memoized against the compiler's reach: the rows are built inside a `map`, so every
 * element is fresh on each batch the list reveals and on each toggle, and only `memo`
 * stops an option's own render from being O(options).
 */
const Option = memo(function Option({ option, selected, onToggle }: OptionProps) {
  return (
    <CommandItem
      value={option.label}
      onSelect={() => {
        onToggle(option.value)
      }}
    >
      <Check className={cn('mr-2 size-4', selected ? 'opacity-100' : 'opacity-0')} />
      <span className="flex items-center justify-between w-full">
        <span>{option.renderLabel ?? option.label}</span>
        {option.count != null && (
          <span className="text-xs text-muted-foreground ml-2">{option.count}</span>
        )}
      </span>
    </CommandItem>
  )
})
