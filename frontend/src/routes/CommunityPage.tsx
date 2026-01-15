import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

export default function CommunityPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <Button asChild variant="outline">
        <Link to="/">{t('pages.community.backHome')}</Link>
      </Button>
    </div>
  )
}
