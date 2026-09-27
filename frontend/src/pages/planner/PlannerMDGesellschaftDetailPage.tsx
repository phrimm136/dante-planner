import { Suspense, lazy, useRef } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { PlannerViewer } from './components/plannerViewer/PlannerViewer'
import { PublishedPlannerHeader } from './components/plannerViewer/PublishedPlannerHeader'
import { PlannerDetailFooter } from './components/plannerViewer/PlannerDetailFooter'
import { NearViewportGate } from './components/NearViewportGate'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { PlannerViewerSkeleton } from './components/plannerSkeletons'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'
import { CommunityPlansErrorFallback } from '@/components/feedback/CommunityPlansErrorFallback'
import { usePublishedPlannerQuery, isPlannerRemoved } from './hooks/usePublishedPlannerQuery'
import { isMDPlanner } from './types/PlannerTypes'
import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useMDGesellschaftFilters } from './hooks/useMDGesellschaftFilters'
import type { UseMDGesellschaftFiltersResult } from './hooks/useMDGesellschaftFilters'
import { SECTION_STYLES } from '@/lib/constants'

const CommentSection = lazy(() =>
  import('@/shared/comment').then((m) => ({ default: m.CommentSection })),
)
const PublishedPlannerList = lazy(() =>
  import('./components/plannerList/PublishedPlannerList').then((m) => ({
    default: m.PublishedPlannerList,
  })),
)
const MDPlannerToolbar = lazy(() =>
  import('./components/plannerList/MDPlannerToolbar').then((m) => ({
    default: m.MDPlannerToolbar,
  })),
)
const PlannerListFilterPills = lazy(() =>
  import('./components/plannerList/PlannerListFilterPills').then((m) => ({
    default: m.PlannerListFilterPills,
  })),
)

export default function PlannerMDGesellschaftDetailPage() {
  const { id } = useParams({ from: '/planner/md/gesellschaft/$id' })

  return (
    <ErrorBoundary>
      <div className={SECTION_STYLES.LAYOUT.page}>
        <Suspense fallback={<PlannerViewerSkeleton />}>
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

  const queryState = usePublishedPlannerQuery(plannerId)

  const { data: user } = useAuthQuery()
  const isAuthenticated = user !== null

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  const { filters, setFilters } = useMDGesellschaftFilters()

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

  const isOwner =
    isAuthenticated &&
    user !== null &&
    user.usernameEpithet === apiData.authorUsernameEpithet &&
    user.usernameSuffix === apiData.authorUsernameSuffix

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
      <PublishedPlannerHeader
        planner={apiData}
        isOwner={isOwner}
        isAuthenticated={isAuthenticated}
        syncEnabled={syncEnabled}
        savedPlannerData={isOwner ? planner : undefined}
        onEdit={isOwner ? handleEdit : undefined}
        onCommentClick={scrollToComments}
      />

      <PlannerViewer planner={planner} />

      <PlannerDetailFooter planner={apiData} isOwner={isOwner} isAuthenticated={isAuthenticated} />

      <div ref={commentsRef}>
        <NearViewportGate placeholder={commentPlaceholder}>
          <Suspense fallback={commentPlaceholder}>
            <CommentSection
              plannerId={plannerId}
              isPublished={true}
              isAuthenticated={isAuthenticated}
            />
          </Suspense>
        </NearViewportGate>
      </div>

      <div className="border-t border-border my-8" />

      <NearViewportGate placeholder={listPlaceholder}>
        <Suspense fallback={listPlaceholder}>
          <PlannerListSection
            filters={filters}
            setFilters={setFilters}
            isAuthenticated={isAuthenticated}
          />
        </Suspense>
      </NearViewportGate>
    </div>
  )
}

const commentPlaceholder = (
  <Skeleton data-testid="comment-section-placeholder" className="h-62 w-full" />
)

const listPlaceholder = (
  <div data-testid="planner-list-placeholder">
    <PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />
  </div>
)

interface PlannerListSectionProps {
  filters: UseMDGesellschaftFiltersResult['filters']
  setFilters: UseMDGesellschaftFiltersResult['setFilters']
  isAuthenticated: boolean
}

function PlannerListSection({ filters, setFilters, isAuthenticated }: PlannerListSectionProps) {
  return (
    <div className={SECTION_STYLES.SPACING.section}>
      <div className="mb-4">
        <MDPlannerToolbar
          search={filters.search}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
          showModeToggle
          mode={filters.mode}
          onModeChange={(m) => setFilters({ mode: m, page: 0 })}
        />
      </div>

      <div className="mb-6">
        <PlannerListFilterPills
          selectedCategory={filters.category}
          onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
        />
      </div>

      <ReactErrorBoundary FallbackComponent={CommunityPlansErrorFallback}>
        <Suspense fallback={<PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />}>
          <PublishedPlannerList
            filters={{
              ...filters,
              keyword: undefined,
              identity: undefined,
              ego: undefined,
              gift: undefined,
              themePack: undefined,
            }}
            isAuthenticated={isAuthenticated}
            onPageChange={(p) => setFilters({ page: p })}
          />
        </Suspense>
      </ReactErrorBoundary>
    </div>
  )
}
