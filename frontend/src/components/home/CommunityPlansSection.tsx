/**
 * CommunityPlansSection - Home page right column
 *
 * Displays community planner cards with Latest/Recommended tabs.
 * Uses useMDGesellschaftData hook and PublishedPlannerCard component.
 *
 * Pattern: PlannerMDGesellschaftPage.tsx (tabs, data fetching)
 */

import { Suspense, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import { PublishedPlannerCard } from '@/components/plannerList/PublishedPlannerCard'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'

import { useMDGesellschaftData } from '@/hooks/useMDGesellschaftData'
import { CARD_GRID } from '@/lib/constants'
import { cn } from '@/lib/utils'

import type { MDGesellschaftMode } from '@/types/MDPlannerListTypes'

/** Number of plans to show on home page */
const HOME_PLANS_LIMIT = 5

// ============================================================================
// Inner Content Component
// ============================================================================

interface CommunityPlansContentProps {
  mode: MDGesellschaftMode
}

/**
 * Inner component that uses Suspense-aware query hook.
 * Must be wrapped in Suspense boundary.
 */
function CommunityPlansContent({ mode }: CommunityPlansContentProps) {
  const { t } = useTranslation('common')
  const { data } = useMDGesellschaftData({
    mode,
    page: 0,
  })

  // Limit to HOME_PLANS_LIMIT items
  const planners = data.content.slice(0, HOME_PLANS_LIMIT)

  if (planners.length === 0) {
    return (
      <div className="text-center text-muted-foreground py-8">
        {t('pages.home.communityPlans.empty')}
      </div>
    )
  }

  return (
    <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.PLANNER}>
      {planners.map((planner) => (
        <Link
          key={planner.id}
          to="/planner/md/gesellschaft/$id"
          params={{ id: planner.id }}
        >
          <PublishedPlannerCard planner={planner} />
        </Link>
      ))}
    </ResponsiveCardGrid>
  )
}

// ============================================================================
// Loading Skeleton
// ============================================================================

function CommunityPlansSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-24 w-full" />
      ))}
    </div>
  )
}

// ============================================================================
// Main Section Component
// ============================================================================

/**
 * Community Plans section for home page.
 * Shows planner cards with Latest/Recommended tab switcher.
 */
export function CommunityPlansSection() {
  const { t } = useTranslation('common')
  const [mode, setMode] = useState<MDGesellschaftMode>('published')

  return (
    <section className="flex flex-col gap-4">
      {/* Header with browse link */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">
          {t('pages.home.communityPlans.title')}
        </h2>
        <Link
          to="/planner/md/gesellschaft"
          className={cn(
            'flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors'
          )}
        >
          {t('pages.home.communityPlans.browseAll')}
          <ArrowRight className="size-4" />
        </Link>
      </div>

      {/* Content container */}
      <div className="bg-muted border border-border rounded-md p-6">
        {/* Tab switcher */}
        <Tabs
          value={mode}
          onValueChange={(v) => setMode(v as MDGesellschaftMode)}
          className="mb-4"
        >
          <TabsList>
            <TabsTrigger value="published">
              {t('pages.home.communityPlans.tabLatest')}
            </TabsTrigger>
            <TabsTrigger value="best">
              {t('pages.home.communityPlans.tabRecommended')}
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* Content with Suspense */}
        <Suspense fallback={<CommunityPlansSkeleton />}>
          <CommunityPlansContent mode={mode} />
        </Suspense>
      </div>
    </section>
  )
}
