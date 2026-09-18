import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle, Search, FileText } from 'lucide-react'

import { Button } from '@/components/ui/button'

import type { PlannerListView } from '../../types/PlannerListTypes'

interface PlannerEmptyStateProps {
  view: PlannerListView
  isFiltered: boolean
}

export function PlannerEmptyState({ view, isFiltered }: PlannerEmptyStateProps) {
  const { t } = useTranslation(['planner', 'common'])

  if (isFiltered) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
        <Search className="size-12 text-muted-foreground mb-4" />
        <h3 className="text-lg font-medium mb-2">{t('pages.plannerList.empty.noMatchTitle')}</h3>
        <p className="text-muted-foreground max-w-md">
          {t('pages.plannerList.empty.noMatchDescription')}
        </p>
      </div>
    )
  }

  if (view === 'my-plans') {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
        <FileText className="size-12 text-muted-foreground mb-4" />
        <h3 className="text-lg font-medium mb-2">{t('pages.plannerList.empty.noPlansTitle')}</h3>
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
