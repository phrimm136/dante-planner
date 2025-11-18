import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/components/common/SearchBar'

interface EGOSearchBarProps {
  searchQuery: string
  onSearchChange: (query: string) => void
}

export function EGOSearchBar({ searchQuery, onSearchChange }: EGOSearchBarProps) {
  const { t } = useTranslation()

  return (
    <SearchBar
      searchQuery={searchQuery}
      onSearchChange={onSearchChange}
      placeholder={t('pages.ego.searchBar')}
    />
  )
}
