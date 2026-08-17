import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { SECTION_STYLES } from '@/lib/constants'

export default function PlannerPage() {
  const { t } = useTranslation()

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <Button asChild variant="outline">
        <Link to="/">{t('pages.planner.backHome')}</Link>
      </Button>
    </div>
  )
}
