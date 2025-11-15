import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

const DEBOUNCE_DELAY = 100

interface IdentitySearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
}

export function IdentitySearchBar({ searchQuery, onSearchChange }: IdentitySearchBarProps) {
  const { t } = useTranslation()
  const [inputValue, setInputValue] = useState(searchQuery)

  // Debounce the search query
  useEffect(() => {
    const timer = setTimeout(() => {
      onSearchChange(inputValue.trim())
    }, DEBOUNCE_DELAY)

    return () => clearTimeout(timer)
  }, [inputValue, onSearchChange])

  // Sync with external changes
  useEffect(() => {
    setInputValue(searchQuery)
  }, [searchQuery])

  return (
    <div className="bg-card border border-border rounded-md p-2 h-14 flex items-center gap-2">
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
        placeholder={t('pages.identity.searchBar')}
        className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
      />
    </div>
  )
}
