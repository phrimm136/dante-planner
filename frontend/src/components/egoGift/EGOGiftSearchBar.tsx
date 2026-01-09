import { SearchBar } from '@/components/common/SearchBar'

interface EGOGiftSearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
  placeholder?: string
}

export function EGOGiftSearchBar({
  searchQuery,
  onSearchChange,
  placeholder = 'Search EGO Gifts...'
}: EGOGiftSearchBarProps) {
  return (
    <SearchBar
      searchQuery={searchQuery}
      onSearchChange={onSearchChange}
      placeholder={placeholder}
    />
  )
}
