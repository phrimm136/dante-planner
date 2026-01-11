import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useMDGesellschaftData } from '@/hooks/useMDGesellschaftData'
import { VotingAnalytics } from './VotingAnalytics'
import { HideReasonModal } from './HideReasonModal'

interface PlannerRowProps {
  planner: {
    id: string
    title: string
    category: string
    upvotes: number
    downvotes: number
    publishedAt?: string
  }
}

function PlannerRow({ planner }: PlannerRowProps) {
  const { t } = useTranslation('planner')
  const [hideModalOpen, setHideModalOpen] = useState(false)

  return (
    <>
      <div className="border rounded-lg p-4 space-y-4">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold">{planner.title}</h3>
            <p className="text-sm text-muted-foreground">{planner.category}</p>
          </div>
          <Button variant="destructive" size="sm" onClick={() => setHideModalOpen(true)}>
            {t('moderation.hideButton')}
          </Button>
        </div>

        <VotingAnalytics
          upvotes={planner.upvotes}
          downvotes={planner.downvotes}
          publishedAt={planner.publishedAt}
        />
      </div>

      <HideReasonModal
        open={hideModalOpen}
        onOpenChange={setHideModalOpen}
        plannerId={planner.id}
        plannerTitle={planner.title}
      />
    </>
  )
}

/**
 * RecommendedPlannerList - Moderator review tab for recommended planners
 *
 * Shows currently recommended planners with voting analytics and hide controls.
 * Moderators can hide planners from recommended without deleting votes.
 */
export function RecommendedPlannerList() {
  const { t } = useTranslation('planner')
  const { data } = useMDGesellschaftData({ recommended: true })

  if (!data || data.planners.length === 0) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        {t('common.noResults')}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {data.planners.map((planner) => (
        <PlannerRow key={planner.id} planner={planner} />
      ))}
    </div>
  )
}

export function RecommendedPlannerListWithSuspense() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <RecommendedPlannerList />
    </Suspense>
  )
}
