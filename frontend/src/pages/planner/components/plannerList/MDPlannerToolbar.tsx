import { useTranslation } from 'react-i18next'

import { SearchBar } from '@/shared/filter'
import { Button } from '@/components/ui/button'

import type { MDGesellschaftMode } from '../../types/MDPlannerListTypes'

interface MDPlannerToolbarProps {
  search: string
  onSearchChange: (value: string) => void
  showModeToggle?: boolean
  mode?: MDGesellschaftMode
  onModeChange?: (mode: MDGesellschaftMode) => void
}

export function MDPlannerToolbar({
  search,
  onSearchChange,
  showModeToggle = false,
  mode = 'published',
  onModeChange,
}: MDPlannerToolbarProps) {
  const { t } = useTranslation(['planner', 'common'])

  const searchPlaceholder = showModeToggle
    ? t('toolbar.searchPlaceholderGesellschaft')
    : t('toolbar.searchPlaceholder')

  return (
    <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
      <div className="flex-1 min-w-0">
        <SearchBar
          searchQuery={search}
          onSearchChange={onSearchChange}
          placeholder={searchPlaceholder}
          className={'h-8'}
        />
      </div>

      {showModeToggle && (
        <div className="flex gap-1">
          <Button
            variant={mode === 'published' ? 'default' : 'outline'}
            size="sm"
            onClick={() => {
              onModeChange?.('published')
            }}
          >
            {t('toolbar.allPublished')}
          </Button>
          <Button
            variant={mode === 'best' ? 'default' : 'outline'}
            size="sm"
            onClick={() => {
              onModeChange?.('best')
            }}
          >
            {t('toolbar.bestOnly')}
          </Button>
        </div>
      )}
    </div>
  )
}
