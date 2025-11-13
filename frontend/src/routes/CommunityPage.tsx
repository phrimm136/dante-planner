import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

export default function CommunityPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.community.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.community.description')}
      </p>
      <Button asChild variant="outline">
        <Link to="/">{t('pages.community.backHome')}</Link>
      </Button>
    </div>
  )
}
