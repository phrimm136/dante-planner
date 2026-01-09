import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * Planner MD Edit Page - Edit an existing planner
 * TODO: Implement full edit functionality, reusing PlannerMDNewPage logic
 */
export default function PlannerMDEditPage() {
  const { id } = useParams({ from: '/planner/md/$id/edit' })
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="container mx-auto py-6">
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">{t('pages.edit.title')}</h1>
        <p className="text-muted-foreground">Planner ID: {id}</p>
        <p className="text-muted-foreground">
          {t('pages.edit.underConstruction')}
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.edit.backToList')}</Link>
        </Button>
      </div>
    </div>
  )
}
