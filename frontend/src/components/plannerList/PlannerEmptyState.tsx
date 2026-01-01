import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle, Search, FileText } from 'lucide-react'

import { Button } from '@/components/ui/button'

import type { PlannerListView } from '@/types/PlannerListTypes'

interface PlannerEmptyStateProps {
  /** Current view mode */
  view: PlannerListView
  /** Whether any filters are applied */
  isFiltered: boolean
}

/**
 * Empty state messaging for planner list.
 *
 * Shows different messages based on context:
 * - My Plans (no filters): "No plans yet. Create your first plan!"
 * - My Plans (filtered): "No plans match your filters."
 * - Community (no filters): "No plans yet. Be the first to share!"
 * - Community (filtered): "No plans match your filters."
 *
 * @example
 * {planners.length === 0 && (
 *   <PlannerEmptyState view={view} isFiltered={hasFilters} />
 * )}
 */
export function PlannerEmptyState({ view, isFiltered }: PlannerEmptyStateProps) {
  const { t } = useTranslation()

  // Filtered state - show search message for both views
  if (isFiltered) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
        <Search className="size-12 text-muted-foreground mb-4" />
        <h3 className="text-lg font-medium mb-2">
          {t('pages.plannerList.empty.noMatchTitle')}
        </h3>
        <p className="text-muted-foreground max-w-md">
          {t('pages.plannerList.empty.noMatchDescription')}
        </p>
      </div>
    )
  }

  // My Plans - no planners yet
  if (view === 'my-plans') {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
        <FileText className="size-12 text-muted-foreground mb-4" />
        <h3 className="text-lg font-medium mb-2">
          {t('pages.plannerList.empty.noPlansTitle')}
        </h3>
        <p className="text-muted-foreground max-w-md mb-6">
          {t('pages.plannerList.empty.noPlansDescription')}
        </p>
        <Button asChild>
          <Link to="/planner/md/new">
            <PlusCircle className="size-4" />
            {t('pages.plannerList.empty.createButton')}
          </Link>
        </Button>
      </div>
    )
  }

  // Community - no planners yet
  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
      <FileText className="size-12 text-muted-foreground mb-4" />
      <h3 className="text-lg font-medium mb-2">
        {t('pages.plannerList.empty.noCommunityPlansTitle')}
      </h3>
      <p className="text-muted-foreground max-w-md mb-6">
        {t('pages.plannerList.empty.noCommunityPlansDescription')}
      </p>
      <Button asChild>
        <Link to="/planner/md/new">
          <PlusCircle className="size-4" />
          {t('pages.plannerList.empty.createAndShare')}
        </Link>
      </Button>
    </div>
  )
}
