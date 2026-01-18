/**
 * MD Planner Toolbar
 *
 * Toolbar for MD planner list pages.
 * - Search bar for filtering planners by title
 * - Mode toggle for gesellschaft view (all published vs best)
 *
 * Note: Sort dropdown removed per spec (no sorting supported).
 *
 * Pattern: Simplified from PlannerListToolbar
 */

import { useTranslation } from 'react-i18next'

import { SearchBar } from '@/components/common/SearchBar'
import { Button } from '@/components/ui/button'

import type { MDGesellschaftMode } from '@/types/MDPlannerListTypes'

// ============================================================================
// Component Props
// ============================================================================

interface MDPlannerToolbarProps {
  /** Current search query */
  search: string
  /** Callback when search changes */
  onSearchChange: (value: string) => void
  /** Whether to show mode toggle (gesellschaft view only) */
  showModeToggle?: boolean
  /** Current mode (published or best) */
  mode?: MDGesellschaftMode
  /** Callback when mode changes */
  onModeChange?: (mode: MDGesellschaftMode) => void
}

// ============================================================================
// Component
// ============================================================================

/**
 * Toolbar with search bar and optional mode toggle.
 *
 * @example
 * ```tsx
 * // For user planners (no mode toggle)
 * <MDPlannerToolbar
 *   search={search}
 *   onSearchChange={(q) => setFilters({ q, page: 0 })}
 * />
 *
 * // For gesellschaft planners (with mode toggle)
 * <MDPlannerToolbar
 *   search={search}
 *   onSearchChange={(q) => setFilters({ q, page: 0 })}
 *   showModeToggle
 *   mode={mode}
 *   onModeChange={(m) => setFilters({ mode: m, page: 0 })}
 * />
 * ```
 */
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
      {/* Search Bar */}
      <div className="flex-1 min-w-0">
        <SearchBar
          searchQuery={search}
          onSearchChange={onSearchChange}
          placeholder={searchPlaceholder}
          className={"h-8"}
        />
      </div>

      {/* Mode Toggle (gesellschaft only) - Two buttons for clear selection */}
      {showModeToggle && (
        <div className="flex gap-1">
          <Button
            variant={mode === 'published' ? 'default' : 'outline'}
            size="sm"
            onClick={() => { onModeChange?.('published') }}
          >
            {t('toolbar.allPublished')}
          </Button>
          <Button
            variant={mode === 'best' ? 'default' : 'outline'}
            size="sm"
            onClick={() => { onModeChange?.('best') }}
          >
            {t('toolbar.bestOnly')}
          </Button>
        </div>
      )}
    </div>
  )
}
