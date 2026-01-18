import { useState, useEffect, useTransition, memo } from 'react'

import { cn } from '@/lib/utils'
import { SEARCH_DEBOUNCE_DELAY } from '@/lib/constants'

interface SearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
  placeholder: string
  className?: string
}

export const SearchBar = memo(function SearchBar({ searchQuery, onSearchChange, placeholder, className }: SearchBarProps) {
  const [inputValue, setInputValue] = useState(searchQuery)
  const [, startTransition] = useTransition()

  // Debounce the search query, using startTransition to keep UI responsive
  useEffect(() => {
    const timer = setTimeout(() => {
      startTransition(() => {
        onSearchChange(inputValue.trim())
      })
    }, SEARCH_DEBOUNCE_DELAY)

    return () => clearTimeout(timer)
  }, [inputValue, onSearchChange, startTransition])

  // Sync with external changes
  useEffect(() => {
    setInputValue(searchQuery)
  }, [searchQuery])

  return (
    <div className={cn("bg-card border box-border border-border rounded-md p-2 h-14 flex items-center", className)}>
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
}, (prev, next) => {
  return prev.searchQuery === next.searchQuery && prev.placeholder === next.placeholder && prev.className === next.className
  // onSearchChange excluded - callback identity changes but behavior is same
})
