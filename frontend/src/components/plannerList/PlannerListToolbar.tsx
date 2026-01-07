import { useTranslation } from 'react-i18next'
import { ArrowDownAZ, Clock, TrendingUp, ThumbsUp } from 'lucide-react'

import { cn } from '@/lib/utils'
import { SearchBar } from '@/components/common/SearchBar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'

import type { PlannerSortOption } from '@/types/PlannerListTypes'

interface PlannerListToolbarProps {
  /** Current search query */
  search: string
  /** Callback when search changes */
  onSearchChange: (value: string) => void
  /** Current sort option */
  sort: PlannerSortOption
  /** Callback when sort changes */
  onSortChange: (sort: PlannerSortOption) => void
  /** Whether to show recommended toggle (community view only) */
  showRecommendedToggle?: boolean
  /** Whether recommended filter is active */
  isRecommended?: boolean
  /** Callback when recommended toggle changes */
  onRecommendedChange?: (value: boolean) => void
}

/**
 * Sort option configuration with icons and i18n keys
 */
const SORT_OPTIONS: { value: PlannerSortOption; i18nKey: string; icon: typeof Clock }[] = [
  { value: 'recent', i18nKey: 'pages.plannerList.sort.recent', icon: Clock },
  { value: 'popular', i18nKey: 'pages.plannerList.sort.popular', icon: TrendingUp },
  { value: 'votes', i18nKey: 'pages.plannerList.sort.votes', icon: ThumbsUp },
]

/**
 * Toolbar with search bar and sort dropdown for planner list.
 * Optional recommended toggle for community view.
 *
 * @example
 * const { search, sort, setFilters } = usePlannerListFilters();
 *
 * <PlannerListToolbar
 *   search={search ?? ''}
 *   onSearchChange={(q) => setFilters({ q, page: 0 })}
 *   sort={sort}
 *   onSortChange={(s) => setFilters({ sort: s, page: 0 })}
 * />
 */
export function PlannerListToolbar({
  search,
  onSearchChange,
  sort,
  onSortChange,
  showRecommendedToggle = false,
  isRecommended = false,
  onRecommendedChange,
}: PlannerListToolbarProps) {
  const { t } = useTranslation(['planner', 'common'])
  const currentSort = SORT_OPTIONS.find((opt) => opt.value === sort) ?? SORT_OPTIONS[0]
  const SortIcon = currentSort.icon

  return (
    <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
      {/* Search Bar */}
      <div className="flex-1 min-w-0">
        <SearchBar
          searchQuery={search}
          onSearchChange={onSearchChange}
          placeholder={t('pages.plannerList.toolbar.searchPlaceholder')}
        />
      </div>

      <div className="flex gap-2">
        {/* Recommended Toggle */}
        {showRecommendedToggle && (
          <Button
            variant={isRecommended ? 'default' : 'outline'}
            size="sm"
            onClick={() => { onRecommendedChange?.(!isRecommended) }}
          >
            {t('pages.plannerList.toolbar.recommended')}
          </Button>
        )}

        {/* Sort Dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm" className="gap-2">
              <SortIcon className="size-4" />
              <span className="hidden sm:inline">{t(currentSort.i18nKey)}</span>
              <ArrowDownAZ className="size-4 sm:hidden" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {SORT_OPTIONS.map((option) => {
              const Icon = option.icon
              return (
                <DropdownMenuItem
                  key={option.value}
                  onClick={() => { onSortChange(option.value) }}
                  className={cn(
                    sort === option.value && 'bg-accent'
                  )}
                >
                  <Icon className="size-4" />
                  {t(option.i18nKey)}
                </DropdownMenuItem>
              )
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}
