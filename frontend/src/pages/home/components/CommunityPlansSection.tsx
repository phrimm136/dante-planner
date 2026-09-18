import { Suspense, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import { PublishedPlannerCard } from '@/pages/planner'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { CommunityPlansErrorFallback } from '@/components/feedback/CommunityPlansErrorFallback'

import { useMDGesellschaftData } from '@/pages/planner'
import { SECTION_STYLES } from '@/lib/constants'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'
import { cn } from '@/lib/utils'

import type { MDGesellschaftMode } from '@/pages/planner'

const HOME_PLANS_LIMIT = 5

interface CommunityPlansContentProps {
  mode: MDGesellschaftMode
}

function CommunityPlansContent({ mode }: CommunityPlansContentProps) {
  const { t } = useTranslation('common')
  const { data } = useMDGesellschaftData({
    mode,
    page: 0,
  })

  const planners = data.content.slice(0, HOME_PLANS_LIMIT)

  if (planners.length === 0) {
    return (
      <div className="text-center text-muted-foreground py-8">
        {t('pages.home.communityPlans.empty')}
      </div>
    )
  }

  return (
    <ResponsiveCardGrid size={PLANNER_GEOMETRY.size} rows="content">
      {planners.map((planner) => (
        <Link key={planner.id} to="/planner/md/gesellschaft/$id" params={{ id: planner.id }}>
          <PublishedPlannerCard planner={planner} />
        </Link>
      ))}
    </ResponsiveCardGrid>
  )
}

function CommunityPlansSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.column}>
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-24 w-full" />
      ))}
    </div>
  )
}

export function CommunityPlansSection() {
  const { t } = useTranslation('common')
  const [mode, setMode] = useState<MDGesellschaftMode>('published')

  return (
    <section className={SECTION_STYLES.LAYOUT.column}>
      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <h2 className="text-xl font-semibold">{t('pages.home.communityPlans.title')}</h2>
        <Link
          to="/planner/md/gesellschaft"
          className={cn(
            'flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors',
          )}
        >
          {t('pages.home.communityPlans.browseAll')}
          <ArrowRight className="size-4" />
        </Link>
      </div>

      <div className={cn(SECTION_STYLES.panel, 'flex-1')}>
        <Tabs value={mode} onValueChange={(v) => setMode(v as MDGesellschaftMode)} className="mb-4">
          <TabsList>
            <TabsTrigger value="published">{t('pages.home.communityPlans.tabLatest')}</TabsTrigger>
            <TabsTrigger value="best">{t('pages.home.communityPlans.tabRecommended')}</TabsTrigger>
          </TabsList>
        </Tabs>

        <ReactErrorBoundary FallbackComponent={CommunityPlansErrorFallback}>
          <Suspense fallback={<CommunityPlansSkeleton />}>
            <CommunityPlansContent mode={mode} />
          </Suspense>
        </ReactErrorBoundary>
      </div>
    </section>
  )
}
