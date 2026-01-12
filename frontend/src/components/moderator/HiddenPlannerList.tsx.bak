import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useHiddenPlannersQuery } from '@/hooks/useHiddenPlannersQuery'
import { useUnhideFromRecommendedMutation } from '@/hooks/useUnhideFromRecommendedMutation'
import { formatDate } from '@/lib/formatDate'

interface HiddenPlannerRowProps {
  planner: {
    id: string
    title: string
    category: string
    authorUsernameKeyword: string
    authorUsernameSuffix: string
    hiddenReason: string
    hiddenByModeratorUsername: string
    hiddenAt: string
  }
}

function HiddenPlannerRow({ planner }: HiddenPlannerRowProps) {
  const { t } = useTranslation('planner')
  const unhide = useUnhideFromRecommendedMutation()

  const handleUnhide = () => {
    unhide.mutate({ plannerId: planner.id })
  }

  return (
    <div className="border rounded-lg p-4 space-y-2">
      <div className="flex justify-between items-start">
        <div>
          <h3 className="font-semibold">{planner.title}</h3>
          <p className="text-sm text-muted-foreground">
            {planner.authorUsernameKeyword}-{planner.authorUsernameSuffix} • {planner.category}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={handleUnhide}
          disabled={unhide.isPending}
        >
          {unhide.isPending ? t('common.loading') : t('moderation.unhideButton')}
        </Button>
      </div>

      <div className="bg-muted p-3 rounded">
        <p className="text-sm font-medium">{t('moderation.hideReason')}</p>
        <p className="text-sm text-muted-foreground">{planner.hiddenReason}</p>
      </div>

      <div className="flex gap-4 text-xs text-muted-foreground">
        <span>
          {t('moderation.hiddenBy')}: {planner.hiddenByModeratorUsername}
        </span>
        <span>
          {t('moderation.hiddenAt')}: {formatDate(planner.hiddenAt)}
        </span>
      </div>
    </div>
  )
}

function HiddenPlannerListContent() {
  const { t } = useTranslation('planner')
  const { data } = useHiddenPlannersQuery(0, 20)

  if (!data || data.content.length === 0) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        {t('common.noResults')}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {data.content.map((planner) => (
        <HiddenPlannerRow key={planner.id} planner={planner} />
      ))}
    </div>
  )
}

/**
 * HiddenPlannerList - Moderator tab showing hidden planners
 *
 * Displays all planners hidden from recommended with unhide controls.
 * Shows hide reason, moderator who hid it, and timestamp.
 */
export function HiddenPlannerList() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <HiddenPlannerListContent />
    </Suspense>
  )
}
