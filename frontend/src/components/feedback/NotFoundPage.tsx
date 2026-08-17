import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * NotFoundPage - 404 error page
 *
 * Displayed when user navigates to a route that doesn't exist
 */
export default function NotFoundPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-6 text-center py-12">
      <h1 className={SECTION_STYLES.TEXT.pageTitle}>{t('errors.notFound.title')}</h1>
      <p className={SECTION_STYLES.TEXT.muted}>{t('errors.notFound.message')}</p>
      <Button asChild variant="outline">
        <Link to="/">{t('errors.notFound.goHome')}</Link>
      </Button>
    </div>
  )
}
