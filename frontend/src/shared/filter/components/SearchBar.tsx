import { useState, useEffect, useTransition } from 'react'

import { cn } from '@/lib/utils'
import { SEARCH_DEBOUNCE_DELAY } from '@/lib/constants'

interface SearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
  placeholder: string
  className?: string
}

export const SearchBar = function SearchBar({
  searchQuery,
  onSearchChange,
  placeholder,
  className,
}: SearchBarProps) {
  const [inputValue, setInputValue] = useState(searchQuery)
  const [appliedQuery, setAppliedQuery] = useState(searchQuery)
  const [, startTransition] = useTransition()

  // A query changed from outside (reset button, URL) replaces the local draft.
  // Adjusting during render keeps the draft and the debounce below consistent
  // within the same pass, so no timer is ever armed against a stale draft.
  if (searchQuery !== appliedQuery) {
    setAppliedQuery(searchQuery)
    setInputValue(searchQuery)
  }

  // Debounce the search query, using startTransition to keep UI responsive
  useEffect(() => {
    const trimmedInput = inputValue.trim()

    // Skip if value hasn't actually changed from the current searchQuery
    if (trimmedInput === searchQuery) {
      return
    }

    const timer = setTimeout(() => {
      startTransition(() => {
        onSearchChange(trimmedInput)
      })
    }, SEARCH_DEBOUNCE_DELAY)

    return () => clearTimeout(timer)
  }, [inputValue, onSearchChange, startTransition, searchQuery])

  return (
    <div
      className={cn(
        'bg-card border box-border border-border rounded-md p-2 h-14 flex items-center',
        className,
      )}
    >
      {/* Magnifier Icon */}
      <div className="shrink-0 w-8 h-8 flex items-center justify-center text-muted-foreground">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
      </div>

      {/* Search Input */}
      <input
        type="text"
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        placeholder={placeholder}
        className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground min-w-0"
      />
    </div>
  )
}
