import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { FILTER_SIDEBAR_WIDTH } from '@/lib/constants'

interface FilterSidebarProps {
  children: React.ReactNode
  primaryFilters?: React.ReactNode
  secondaryFilters?: React.ReactNode
  searchBar?: React.ReactNode
  activeFilterCount?: number
  onResetAll?: (() => void) | undefined
}

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
      <aside
        className={cn(
          'hidden lg:block',
          'sticky top-4 self-start',
          'max-h-[calc(100vh-2rem)] overflow-y-auto',
          'rounded-lg border bg-card p-3',
        )}
        style={{ width: `${FILTER_SIDEBAR_WIDTH}px`, minWidth: `${FILTER_SIDEBAR_WIDTH}px` }}
      >
        <div className="space-y-2">
          <div className="space-y-1">{children}</div>

          {searchBar && <div>{searchBar}</div>}

          {onResetAll && (
            <Button
              variant="outline"
              size="sm"
              onClick={onResetAll}
              disabled={!hasActiveFilters}
              className={cn(
                'w-full',
                hasActiveFilters &&
                  'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive',
              )}
            >
              {t('filters.resetAll', 'Reset All')}
              {hasActiveFilters && (
                <span className="ml-1 text-muted-foreground">({activeFilterCount})</span>
              )}
            </Button>
          )}
        </div>
      </aside>

      <div className="lg:hidden w-full relative">
        <div className="rounded-lg border bg-card p-3">
          <div className="space-y-1">
            {primaryFilters && <div className="space-y-1">{primaryFilters}</div>}

            {isExpanded && secondaryFilters && <div className="space-y-1">{secondaryFilters}</div>}

            {searchBar && <div>{searchBar}</div>}

            {onResetAll && (
              <Button
                variant="outline"
                size="sm"
                onClick={onResetAll}
                disabled={!hasActiveFilters}
                className={cn(
                  'w-full',
                  hasActiveFilters &&
                    'hover:bg-destructive hover:text-destructive-foreground hover:border-destructive',
                )}
              >
                {t('filters.resetAll', 'Reset All')}
                {hasActiveFilters && (
                  <span className="ml-1 text-muted-foreground">({activeFilterCount})</span>
                )}
              </Button>
            )}
          </div>
        </div>

        {secondaryFilters && (
          <button
            type="button"
            onClick={() => {
              setIsExpanded(!isExpanded)
            }}
            aria-expanded={isExpanded}
            aria-label={
              isExpanded
                ? t('filters.collapse', 'Collapse filters')
                : t('filters.expand', 'Expand filters')
            }
            className={cn(
              'absolute left-1/2 bottom-0 -translate-x-1/2 translate-y-1/2',
              'size-8 rounded-full',
              'bg-card border border-border',
              'flex items-center justify-center',
              'hover:bg-accent transition-colors',
              'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
            )}
          >
            <ChevronDown
              className={cn('size-4 transition-transform duration-200', isExpanded && 'rotate-180')}
            />
          </button>
        )}
      </div>
    </>
  )
}
