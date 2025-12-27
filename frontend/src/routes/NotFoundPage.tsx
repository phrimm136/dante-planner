import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * NotFoundPage - 404 error page
 *
 * Displayed when user navigates to a route that doesn't exist
 */
export default function NotFoundPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center space-y-6">
        <h1 className="text-6xl font-bold text-destructive">
          {t('errors.notFound.title')}
        </h1>
        <h2 className="text-2xl font-semibold">{t('errors.notFound.heading')}</h2>

        <p className="text-muted-foreground max-w-md">
          {t('errors.notFound.message')}
        </p>

        <div className="space-x-2">
          <Button asChild>
            <Link to="/">{t('errors.notFound.goHome')}</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
