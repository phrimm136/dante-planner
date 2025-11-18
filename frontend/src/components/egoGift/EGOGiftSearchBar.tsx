import { SearchBar } from '@/components/common/SearchBar'

interface EGOGiftSearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
}

export function EGOGiftSearchBar({ searchQuery, onSearchChange }: EGOGiftSearchBarProps) {
  return (
    <SearchBar
      searchQuery={searchQuery}
      onSearchChange={onSearchChange}
      placeholder="Search EGO Gifts..."
    />
  )
}
