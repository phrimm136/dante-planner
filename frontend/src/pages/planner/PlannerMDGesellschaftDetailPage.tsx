import { Suspense, useRef } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { PlannerViewer } from './components/plannerViewer/PlannerViewer'
import { PublishedPlannerHeader } from './components/plannerViewer/PublishedPlannerHeader'
import { PlannerDetailFooter } from './components/plannerViewer/PlannerDetailFooter'
import { CommentSection } from '@/shared/comment'
import { PublishedPlannerList } from './components/plannerList/PublishedPlannerList'
import { MDPlannerToolbar } from './components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from './components/plannerList/PlannerListFilterPills'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { CommunityPlansErrorFallback } from '@/components/feedback/CommunityPlansErrorFallback'
import { usePublishedPlannerQuery, isPlannerRemoved } from './hooks/usePublishedPlannerQuery'
import { isMDPlanner } from './types/PlannerTypes'
import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useMDGesellschaftFilters } from './hooks/useMDGesellschaftFilters'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Planner MD Gesellschaft Detail Page - View a published community planner
 * Displays full header with author info, stats, and engagement actions.
 */
export default function PlannerMDGesellschaftDetailPage() {
  const { id } = useParams({ from: '/planner/md/gesellschaft/$id' })

  return (
    <ErrorBoundary>
      <div className={SECTION_STYLES.LAYOUT.page}>
        <Suspense
          fallback={
            <div className="space-y-6">
              <div className={SECTION_STYLES.LAYOUT.rowBetween}>
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

  // Load published planner from API via Suspense query
  // Returns both apiData (for header/footer) and planner (for viewer)
  const queryState = usePublishedPlannerQuery(plannerId)

  // Get auth state for ownership check and gating actions
  const { data: user } = useAuthQuery()
  const isAuthenticated = user !== null

  // Get sync setting — needed for Apply Latest Mirror handler
  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  // URL search params for list section
  const { category, page, mode, search, setFilters } = useMDGesellschaftFilters()

  if (isPlannerRemoved(queryState)) {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className={SECTION_STYLES.TEXT.pageTitle}>{t('sync.removedOnAnotherDevice')}</h1>
        <Button asChild variant="outline">
          <Link to="/planner/md/gesellschaft">{t('pages.detail.backToList')}</Link>
        </Button>
      </div>
    )
  }

  const { apiData, planner } = queryState

  // Determine ownership by comparing author username with current user's username
  const isOwner =
    isAuthenticated &&
    user !== null &&
    user.usernameEpithet === apiData.authorUsernameEpithet &&
    user.usernameSuffix === apiData.authorUsernameSuffix

  // Validate planner type - viewer only supports Mirror Dungeon planners
  if (!isMDPlanner(planner)) {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className={SECTION_STYLES.TEXT.pageTitle}>{t('pages.detail.invalidType')}</h1>
        <p className={SECTION_STYLES.TEXT.muted}>{t('pages.detail.invalidTypeMessage')}</p>
        <p className={SECTION_STYLES.TEXT.caption}>
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
      <PublishedPlannerHeader
        planner={apiData}
        isOwner={isOwner}
        isAuthenticated={isAuthenticated}
        syncEnabled={syncEnabled}
        savedPlannerData={isOwner ? planner : undefined}
        onEdit={isOwner ? handleEdit : undefined}
        onCommentClick={scrollToComments}
      />

      {/* Planner Viewer */}
      <PlannerViewer planner={planner} />

      {/* Footer with engagement actions */}
      <PlannerDetailFooter planner={apiData} isOwner={isOwner} isAuthenticated={isAuthenticated} />

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
