import { Suspense, useEffect, useRef } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { PlannerViewer } from '@/components/plannerViewer/PlannerViewer'
import { PlannerDetailHeader } from '@/components/plannerViewer/PlannerDetailHeader'
import { PlannerDetailFooter } from '@/components/plannerViewer/PlannerDetailFooter'
import { CommentSection } from '@/components/comment/CommentSection'
import { PublishedPlannerList } from '@/components/plannerList/PublishedPlannerList'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'
import { CommunityPlansErrorFallback } from '@/components/home/CommunityPlansErrorFallback'
import { usePublishedPlannerQuery } from '@/hooks/usePublishedPlannerQuery'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import { usePlannerViewMutation } from '@/hooks/usePlannerViewMutation'
import { useMDGesellschaftFilters } from '@/hooks/useMDGesellschaftFilters'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Planner MD Gesellschaft Detail Page - View a published community planner
 * Displays full header with author info, stats, and engagement actions.
 */
export default function PlannerMDGesellschaftDetailPage() {
  const { id } = useParams({ from: '/planner/md/gesellschaft/$id' })

  return (
    <ErrorBoundary>
      <div className="container mx-auto p-8">
        <Suspense
          fallback={
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <Skeleton className="h-10 w-64" />
                <Skeleton className="h-10 w-32" />
              </div>
              <div className="bg-background rounded-lg p-6 space-y-4">
                <Skeleton className="h-64 w-full" />
                <Skeleton className="h-48 w-full" />
                <Skeleton className="h-96 w-full" />
              </div>
            </div>
          }
        >
          <PublishedPlannerDetailContent plannerId={id} />
        </Suspense>
      </div>
    </ErrorBoundary>
  )
}

function PublishedPlannerDetailContent({ plannerId }: { plannerId: string }) {
  const { t } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()
  const commentsRef = useRef<HTMLDivElement>(null)
  const viewRecordedRef = useRef<string | null>(null)

  // Load published planner from API via Suspense query
  // Returns both apiData (for header/footer) and planner (for viewer)
  const { apiData, planner } = usePublishedPlannerQuery(plannerId)

  // Get auth state for ownership check and gating actions
  const { data: user } = useAuthQuery()
  const isAuthenticated = user !== null

  // URL search params for list section
  const { category, page, mode, search, setFilters } = useMDGesellschaftFilters()

  // Record view on page load (fire-and-forget, backend handles deduplication)
  // Use ref to prevent StrictMode double-invocation and component remount duplicates
  const recordView = usePlannerViewMutation()

  useEffect(() => {
    if (viewRecordedRef.current === plannerId) return
    viewRecordedRef.current = plannerId
    recordView.mutate(plannerId)
  }, [plannerId])

  // Determine ownership by comparing author username with current user's username
  const isOwner = isAuthenticated && user !== null && (
    user.usernameEpithet === apiData.authorUsernameEpithet &&
    user.usernameSuffix === apiData.authorUsernameSuffix
  )

  // Validate planner type - viewer only supports Mirror Dungeon planners
  if (planner.config.type !== 'MIRROR_DUNGEON') {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className="text-2xl font-bold">{t('pages.detail.invalidType')}</h1>
        <p className="text-muted-foreground">{t('pages.detail.invalidTypeMessage')}</p>
        <p className="text-sm text-muted-foreground">
          {t('pages.detail.currentType')}: {planner.config.type}
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md/gesellschaft">{t('pages.detail.backToList')}</Link>
        </Button>
      </div>
    )
  }

  const handleEdit = () => {
    void navigate({
      to: '/planner/md/$id/edit',
      params: { id: plannerId },
    })
  }

  const scrollToComments = () => {
    commentsRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <div className="space-y-4">
      {/* Header with author info, stats, and actions */}
      <PlannerDetailHeader
        variant="published"
        planner={apiData}
        isOwner={isOwner}
        isAuthenticated={isAuthenticated}
        onEdit={isOwner ? handleEdit : undefined}
        onCommentClick={scrollToComments}
      />

      {/* Planner Viewer */}
      <PlannerViewer planner={planner} />

      {/* Footer with engagement actions */}
      <PlannerDetailFooter
        planner={apiData}
        isOwner={isOwner}
        isAuthenticated={isAuthenticated}
      />

      {/* Comment Section */}
      <div ref={commentsRef}>
        <CommentSection
          plannerId={plannerId}
          isPublished={true}
          isAuthenticated={isAuthenticated}
        />
      </div>

      {/* Separator */}
      <div className="border-t border-border my-8" />

      {/* Community Planners List Section */}
      <div className={SECTION_STYLES.SPACING.section}>
        {/* Toolbar: Search + Mode Toggle */}
        <div className="mb-4">
          <MDPlannerToolbar
            search={search}
            onSearchChange={(q) => setFilters({ q, page: 0 })}
            showModeToggle
            mode={mode}
            onModeChange={(m) => setFilters({ mode: m, page: 0 })}
          />
        </div>

        {/* Category Filter Pills */}
        <div className="mb-6">
          <PlannerListFilterPills
            selectedCategory={category}
            onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
          />
        </div>

        {/* Planner List Grid */}
        <ReactErrorBoundary FallbackComponent={CommunityPlansErrorFallback}>
          <Suspense fallback={<PlannerGridSkeleton />}>
            <PublishedPlannerList
              mode={mode}
              category={category}
              page={page}
              search={search}
              isAuthenticated={isAuthenticated}
              onPageChange={(p) => setFilters({ page: p })}
            />
          </Suspense>
        </ReactErrorBoundary>
      </div>
    </div>
  )
}
