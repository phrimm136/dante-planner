import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { FILTER_SIDEBAR_WIDTH } from '@/lib/constants'

interface FilterSidebarProps {
  /** All filter sections (desktop sidebar uses this) */
  children: React.ReactNode
  /** Primary filters always visible on mobile (Sinner, Keyword) */
  primaryFilters?: React.ReactNode
  /** Secondary filters shown when mobile expanded (Skill Attribute, Attack Type, etc.) */
  secondaryFilters?: React.ReactNode
  /** Search bar - visible in mobile collapsed state next to Reset All */
  searchBar?: React.ReactNode
  activeFilterCount?: number
  onResetAll?: () => void
}

/**
 * Responsive filter sidebar wrapper for list pages
 *
 * Desktop (lg+): Sticky sidebar on LEFT side with fixed width
 * Mobile (<lg): Inline expandable filter header at top of page
 *   - Collapsed: primaryFilters + Reset All + Expand button
 *   - Expanded: primaryFilters + secondaryFilters + Reset All + Collapse button
 *
 * Pattern: Named slots composition - primaryFilters/secondaryFilters for mobile, children for desktop
 * Reusable for Identity, EGO, and EGOGift pages
 */
export function FilterSidebar({
  children,
  primaryFilters,
  secondaryFilters,
  searchBar,
  activeFilterCount = 0,
  onResetAll,
}: FilterSidebarProps) {
  const { t } = useTranslation(['database', 'common'])
  const [isExpanded, setIsExpanded] = useState(false)

  const hasActiveFilters = activeFilterCount > 0

  return (
    <>
      {/* Desktop sidebar - visible on lg+ */}
      <aside
        className={cn(
          'hidden lg:block',
          'sticky top-4 self-start',
          'rounded-lg border bg-card p-3'
        )}
        style={{ width: `${FILTER_SIDEBAR_WIDTH}px`, minWidth: `${FILTER_SIDEBAR_WIDTH}px` }}
      >
        <div className="space-y-2">
          {/* Filter sections passed as children */}
          <div className="space-y-1">{children}</div>

          {/* Search Bar */}
          {searchBar && <div>{searchBar}</div>}

          {/* Reset All button */}
          {onResetAll && (
            <Button
              variant="outline"
              size="sm"
              onClick={onResetAll}
              disabled={!hasActiveFilters}
              className={cn(
                'w-full',
                hasActiveFilters && 'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive'
              )}
            >
              {t('filters.resetAll', 'Reset All')}
              {hasActiveFilters && (
                <span className="ml-1 text-muted-foreground">
                  ({activeFilterCount})
                </span>
              )}
            </Button>
          )}
        </div>
      </aside>

      {/* Mobile expandable filter header - visible below lg */}
      <div className="lg:hidden w-full relative">
        <div className="rounded-lg border bg-card p-3">
          <div className="space-y-1">
            {/* Primary filters - always visible */}
            {primaryFilters && (
              <div className="space-y-1">{primaryFilters}</div>
            )}

            {/* Secondary filters - expand in the middle (no separator) */}
            {isExpanded && secondaryFilters && (
              <div className="space-y-1">
                {secondaryFilters}
              </div>
            )}

            {/* Search Bar */}
            {searchBar && <div>{searchBar}</div>}

            {/* Reset All button */}
            {onResetAll && (
              <Button
                variant="outline"
                size="sm"
                onClick={onResetAll}
                disabled={!hasActiveFilters}
                className={cn(
                  'w-full',
                  hasActiveFilters && 'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive'
                )}
              >
                {t('filters.resetAll', 'Reset All')}
                {hasActiveFilters && (
                  <span className="ml-1 text-muted-foreground">
                    ({activeFilterCount})
                  </span>
                )}
              </Button>
            )}
          </div>
        </div>

        {/* Expand/Collapse circle button - centered on bottom border */}
        {secondaryFilters && (
          <button
            type="button"
            onClick={() => { setIsExpanded(!isExpanded) }}
            aria-expanded={isExpanded}
            aria-label={isExpanded ? t('filters.collapse', 'Collapse filters') : t('filters.expand', 'Expand filters')}
            className={cn(
              'absolute left-1/2 bottom-0 -translate-x-1/2 translate-y-1/2',
              'size-8 rounded-full',
              'bg-card border border-border',
              'flex items-center justify-center',
              'hover:bg-accent transition-colors',
              'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2'
            )}
          >
            <ChevronDown
              className={cn(
                'size-4 transition-transform duration-200',
                isExpanded && 'rotate-180'
              )}
            />
          </button>
        )}
      </div>
    </>
  )
}
