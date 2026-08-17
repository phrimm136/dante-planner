import { ChevronDown } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { getKeywordDisplayName } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'

interface KeywordSelectorProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  placeholder: string
  clearLabel: string
  selectedCountText: string
}

export function KeywordSelector({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  placeholder,
  clearLabel,
  selectedCountText,
}: KeywordSelectorProps) {
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
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="min-h-10 w-full p-2 border border-border rounded-md bg-card cursor-pointer hover:border-primary/50 transition-colors flex items-center"
        >
          {selectedOptions.size === 0 ? (
            <span className="text-muted-foreground text-sm">{placeholder}</span>
          ) : (
            <div className="flex flex-wrap gap-1">
              {Array.from(selectedOptions).map((option) => (
                <div
                  key={option}
                  className="w-7 h-7 rounded-md border-2 border-primary bg-primary/10"
                  title={getKeywordDisplayName(option)}
                >
                  <img
                    src={getIconPath(option)}
                    alt={getKeywordDisplayName(option)}
                    className="w-full h-full object-contain"
                  />
                </div>
              ))}
            </div>
          )}
          <ChevronDown className="ml-auto size-4 opacity-50 shrink-0" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-80 p-3">
        <div className="flex justify-between items-center mb-2">
          <span className={SECTION_STYLES.TEXT.caption}>{selectedCountText}</span>
          <Button variant="ghost" size="sm" onClick={clearAll}>
            {clearLabel}
          </Button>
        </div>

        <div className={SECTION_STYLES.LAYOUT.wrap}>
          {options.map((option) => {
            const isSelected = selectedOptions.has(option)
            const label = getKeywordDisplayName(option)
            return (
              <button
                type="button"
                key={option}
                onClick={() => {
                  toggleOption(option)
                }}
                className={`shrink-0 w-10 h-10 rounded-md border-2 transition-all ${
                  isSelected
                    ? 'border-primary bg-primary/10'
                    : 'border-border bg-button hover:border-primary/50'
                }`}
                title={label}
              >
                <img
                  src={getIconPath(option)}
                  alt={label}
                  className="w-full h-full object-contain"
                />
              </button>
            )
          })}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
