import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function MDPlannerNavButtons() {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="flex gap-2">
      <Link to="/planner/md" activeOptions={{ exact: true, includeSearch: false }}>
        {({ isActive }) => (
          <Button
            variant={isActive ? 'default' : 'outline'}
            size="sm"
            className={cn('min-w-[100px]', isActive && 'pointer-events-none')}
          >
            {t('nav.myPlans')}
          </Button>
        )}
      </Link>

      <Link to="/planner/md/gesellschaft" activeOptions={{ exact: true, includeSearch: false }}>
        {({ isActive }) => (
          <Button
            variant={isActive ? 'default' : 'outline'}
            size="sm"
            className={cn('min-w-[100px]', isActive && 'pointer-events-none')}
          >
            {t('nav.gesellschaft')}
          </Button>
        )}
      </Link>
    </div>
  )
}
