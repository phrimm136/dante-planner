import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

interface PlannerNotFoundProps {
  /** Path to the list page (e.g., "/planner/md" or "/planner/md/gesellschaft") */
  listPath: string
}

/**
 * PlannerNotFound - Reusable not found component for planner pages
 *
 * Shows a consistent "planner not found" message with a link back to the list
 */
export function PlannerNotFound({ listPath }: PlannerNotFoundProps) {
  const { t } = useTranslation('planner')

  return (
    <div className="space-y-6 text-center py-12">
      <h1 className="text-2xl font-bold">
        {t('pages.detail.notFound', 'Plan Not Found')}
      </h1>
      <p className="text-muted-foreground">
        {t('pages.detail.notFoundMessage', 'The plan you are looking for does not exist.')}
      </p>
      <Button asChild variant="outline">
        <Link to={listPath}>{t('pages.detail.backToList', 'Back to Plan List')}</Link>
      </Button>
    </div>
  )
}
