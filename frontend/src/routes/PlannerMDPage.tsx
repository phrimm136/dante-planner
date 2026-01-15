/**
 * PlannerMDPage - Personal planners view (My Plans)
 *
 * Route: /planner/md
 *
 * Features:
 * - Shows user's personal planners (IndexedDB for guests, server for auth)
 * - Category filter pills (5F, 10F, 15F)
 * - Paginated card grid
 * - Empty states for no results
 * - Navigation buttons to switch to Gesellschaft view
 *
 * URL state managed via useMDUserFilters hook
 * Data fetched via useMDUserPlannersData hook
 *
 * Pattern: IdentityPage.tsx (Suspense wrapping, filter layout)
 */

import { Suspense, useState, useEffect } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle, FileText, Clock, CheckCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useMDUserPlannersData } from '@/hooks/useMDUserPlannersData'
import { useMDUserFilters } from '@/hooks/useMDUserFilters'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import { CARD_GRID, MD_CATEGORY_STYLES, calculatePlannerPages } from '@/lib/constants'
import { formatPlannerDate } from '@/lib/formatDate'
import { cn } from '@/lib/utils'

import { MDPlannerNavButtons } from '@/components/plannerList/MDPlannerNavButtons'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerEmptyState } from '@/components/plannerList/PlannerEmptyState'
import { PlannerListPagination } from '@/components/plannerList/PlannerListPagination'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { LoadingState } from '@/components/common/LoadingState'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'

import type { MDCategory } from '@/lib/constants'
import type { PlannerSummary } from '@/types/PlannerTypes'

// ============================================================================
// Personal Planner Card Component
// ============================================================================

interface PersonalPlannerCardProps {
  planner: PlannerSummary
}

/**
 * Simple card for displaying personal planner summary
 * Different from PlannerCard (community) - shows status instead of votes
 */
function PersonalPlannerCard({ planner }: PersonalPlannerCardProps) {
  const { t } = useTranslation(['planner', 'common'])
  const authQuery = useAuthQuery()
  const isAuthenticated = authQuery.data !== null
  const categoryClass =
    planner.category in MD_CATEGORY_STYLES
      ? MD_CATEGORY_STYLES[planner.category as MDCategory]
      : 'bg-muted text-muted-foreground'

  // Status display logic
  const statusText = isAuthenticated
    ? planner.status === 'saved'
      ? t('status.synced')
      : t('status.unsynced')
    : planner.status === 'saved'
      ? t('status.saved')
      : t('status.autoSaved')

  return (
    <Link
      to="/planner/md/$id"
      params={{ id: planner.id }}
      className="block"
    >
      <div className="bg-card border border-border rounded-lg p-4 h-full hover:border-primary/50 transition-colors cursor-pointer">
        {/* Category + Status badges */}
        <div className="flex items-center justify-between gap-2 mb-2">
          <span
            className={cn(
              'px-2 py-0.5 text-xs font-medium rounded',
              categoryClass
            )}
          >
            {planner.category}
          </span>
          <span
            className={cn(
              'flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded',
              planner.status === 'saved'
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground'
            )}
          >
            {planner.status === 'saved' ? (
              <><CheckCircle className="size-3" />{statusText}</>
            ) : (
              <><FileText className="size-3" />{statusText}</>
            )}
          </span>
        </div>

        {/* Title */}
        <h3 className="line-clamp-2 text-base font-medium mb-2">
          {planner.title || t('untitled')}
        </h3>

        {/* Last modified */}
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <Clock className="size-3" />
          {formatPlannerDate(planner.lastModifiedAt)}
        </p>
      </div>
    </Link>
  )
}

// ============================================================================
// Inner Content Component
// ============================================================================

interface PlannerMDContentProps {
  category: MDCategory | undefined
  page: number
  search: string
  onPageChange: (page: number) => void
}

/**
 * Inner component that uses Suspense-aware query hooks.
 * Must be wrapped in Suspense boundary.
 */
function PlannerMDContent({
  category,
  page,
  search,
  onPageChange,
}: PlannerMDContentProps) {
  const { planners, totalCount } = useMDUserPlannersData({
    category,
    page,
    search: search || undefined,
  })

  // Progressive rendering: start with 10 cards, add more incrementally
  const [displayCount, setDisplayCount] = useState(10)

  // Reset display count when planners change
  useEffect(() => {
    setDisplayCount(10)
  }, [planners])

  // Progressively render more cards (10 per frame)
  useEffect(() => {
    if (displayCount < planners.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 10, planners.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, planners.length])

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters = !!category || !!search

  // Calculate pagination
  const totalPages = calculatePlannerPages(totalCount)

  // Handle empty state
  if (planners.length === 0) {
    return <PlannerEmptyState view="my-plans" isFiltered={hasActiveFilters} />
  }

  return (
    <>
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.PLANNER}>
        {planners.slice(0, displayCount).map((planner: PlannerSummary) => (
          <PersonalPlannerCard key={planner.id} planner={planner} />
        ))}
      </ResponsiveCardGrid>

      {totalPages > 1 && (
        <PlannerListPagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={onPageChange}
        />
      )}
    </>
  )
}

// ============================================================================
// Page Content Component
// ============================================================================

/**
 * Page content with all filter controls and data fetching.
 * Wrapped in Suspense by the outer PlannerMDPage component.
 */
function PlannerMDPageContent() {
  const { t } = useTranslation(['planner', 'common'])

  const {
    category,
    page,
    search,
    setFilters,
  } = useMDUserFilters()

  return (
    <div className="container mx-auto p-8">
      {/* Create New Button */}
      <div className="flex justify-end mb-6">
        <Button asChild>
          <Link to="/planner/md/new">
            <PlusCircle className="size-4" />
            {t('pages.list.createNew')}
          </Link>
        </Button>
      </div>

      {/* Navigation: My Plans / Gesellschaft */}
      <div className="mb-6">
        <MDPlannerNavButtons />
      </div>

      {/* Toolbar: Search only (no mode toggle for personal planners) */}
      <div className="mb-4">
        <MDPlannerToolbar
          search={search}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
        />
      </div>

      {/* Category Filter Pills */}
      <div className="mb-6">
        <PlannerListFilterPills
          selectedCategory={category}
          onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
        />
      </div>

      {/* Content Grid with inner Suspense for data loading */}
      <Suspense fallback={<PlannerGridSkeleton />}>
        <PlannerMDContent
          category={category}
          page={page}
          search={search}
          onPageChange={(p) => setFilters({ page: p })}
        />
      </Suspense>
    </div>
  )
}

// ============================================================================
// Main Page Component
// ============================================================================

/**
 * PlannerMDPage - Personal planners page with Suspense boundary
 *
 * Pattern: IdentityPage.tsx
 * Outer component wraps inner content in Suspense for loading state
 */
export default function PlannerMDPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlannerMDPageContent />
    </Suspense>
  )
}
