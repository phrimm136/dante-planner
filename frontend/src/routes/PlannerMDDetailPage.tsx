import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * Planner MD Detail Page - View a saved planner
 * TODO: Implement full detail view with read-only state
 */
export default function PlannerMDDetailPage() {
  const { id } = useParams({ from: '/planner/md/$id' })
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="container mx-auto py-6">
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">{t('pages.detail.title')}</h1>
        <p className="text-muted-foreground">Planner ID: {id}</p>
        <p className="text-muted-foreground">
          {t('pages.detail.underConstruction')}
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.detail.backToList')}</Link>
        </Button>
      </div>
    </div>
  )
}
