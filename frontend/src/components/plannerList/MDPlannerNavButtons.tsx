/**
 * MD Planner Navigation Buttons
 *
 * Navigation component for switching between:
 * - /planner/md: Personal planners (My Plans)
 * - /planner/md/gesellschaft: Community planners (Gesellschaft)
 *
 * Uses TanStack Router's activeProps for active state styling.
 *
 * Pattern: Link with activeProps (TanStack Router)
 */

import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// ============================================================================
// Component
// ============================================================================

/**
 * Navigation buttons for MD planner routes
 *
 * @example
 * ```tsx
 * <MDPlannerNavButtons />
 * ```
 */
export function MDPlannerNavButtons() {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="flex gap-2">
      <Link
        to="/planner/md"
        activeOptions={{ exact: true, includeSearch: false }}
      >
        {({ isActive }) => (
          <Button
            variant={isActive ? 'default' : 'outline'}
            size="sm"
            className={cn(
              'min-w-[100px]',
              isActive && 'pointer-events-none'
            )}
          >
            {t('nav.myPlans')}
          </Button>
        )}
      </Link>

      <Link
        to="/planner/md/gesellschaft"
        activeOptions={{ exact: true, includeSearch: false }}
      >
        {({ isActive }) => (
          <Button
            variant={isActive ? 'default' : 'outline'}
            size="sm"
            className={cn(
              'min-w-[100px]',
              isActive && 'pointer-events-none'
            )}
          >
            {t('nav.gesellschaft')}
          </Button>
        )}
      </Link>
    </div>
  )
}
