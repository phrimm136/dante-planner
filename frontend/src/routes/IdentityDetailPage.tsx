import { useParams, Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

export default function IdentityDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams({ strict: false })

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">
        Identity Detail: {id}
      </h1>
      <p className="text-muted-foreground mb-6">
        Placeholder for identity detail page
      </p>

      <Button asChild variant="outline">
        <Link to="/identity">{t('pages.identity.title')}</Link>
      </Button>
    </div>
  )
}
