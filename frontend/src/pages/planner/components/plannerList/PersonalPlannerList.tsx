import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useMDUserPlannersData } from '../../hooks/useMDUserPlannersData'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { PROGRESSIVE_REVEAL, calculatePlannerPages } from '@/lib/constants'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'

import { PersonalPlannerCard } from './PersonalPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { Button } from '@/components/ui/button'
import { BatchConflictDialog } from '../BatchConflictDialog'

import type { ConflictResolution } from '../BatchConflictDialog'
import type { ConflictOutcome } from '../../lib/conflictChoice'
import type { MDCategory } from '@/shared/gameData'
import type { PlannerSummary } from '../../types/PlannerTypes'
import type { PlannerSearchFilters } from '../../types/PlannerSearchTypes'

export interface PersonalPlannerListProps {
  category?: MDCategory | undefined
  page: number
  search?: string | undefined
  contentFilters?: PlannerSearchFilters | undefined
  onPageChange: (page: number) => void
}

export function PersonalPlannerList({
  category,
  page,
  search,
  contentFilters,
  onPageChange,
}: PersonalPlannerListProps) {
  const {
    planners,
    totalCount,
    isAuthenticated,
    isSyncing,
    pendingConflicts,
    resolveConflicts,
    isResolvingConflicts,
  } = useMDUserPlannersData({
    ...(category !== undefined && { category }),
    page,
    ...(search ? { search } : {}),
    ...(contentFilters !== undefined && { contentFilters }),
  })

  const { t } = useTranslation('planner')

  const [outcomes, setOutcomes] = useState<ConflictOutcome[]>([])

  const [dismissed, setDismissed] = useState(false)

  const [batchEpoch, setBatchEpoch] = useState(0)
  const wasPending = useRef(false)

  useEffect(() => {
    const pending = pendingConflicts.length > 0
    if (pending && !wasPending.current) {
      setBatchEpoch((current) => current + 1)
      setDismissed(false)
      setOutcomes([])
    }
    wasPending.current = pending
  }, [pendingConflicts])
  // TODO: Add UI indicator when isSyncing is true
  void isSyncing

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  const displayCount = useProgressiveCount({
    total: planners.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const hasActiveFilters =
    !!category ||
    !!search ||
    !!(
      contentFilters &&
      (contentFilters.title !== null ||
        contentFilters.keywords.length > 0 ||
        contentFilters.identityIds.length > 0 ||
        contentFilters.egoIds.length > 0 ||
        contentFilters.giftIds.length > 0 ||
        contentFilters.themePackIds.length > 0)
    )

  const totalPages = calculatePlannerPages(totalCount)

  const submitResolutions = async (resolutions: ConflictResolution[]) => {
    setOutcomes(await resolveConflicts(resolutions))
  }

  return (
    <>
      <BatchConflictDialog
        key={batchEpoch}
        open={pendingConflicts.length > 0 && !dismissed}
        conflicts={pendingConflicts}
        onResolve={(resolutions) => void submitResolutions(resolutions)}
        isResolving={isResolvingConflicts}
        outcomes={outcomes}
        onDismiss={() => setDismissed(true)}
      />

      {pendingConflicts.length > 0 && dismissed && (
        <div className="mb-4">
          <Button variant="outline" onClick={() => setDismissed(false)}>
            {t('pages.plannerMD.batchConflict.reopen', '{{count}} unresolved conflicts', {
              count: pendingConflicts.length,
            })}
          </Button>
        </div>
      )}

      {planners.length === 0 ? (
        <PlannerEmptyState view="my-plans" isFiltered={hasActiveFilters} />
      ) : (
        <>
          <ResponsiveCardGrid size={PLANNER_GEOMETRY.size} rows="content">
            {planners.slice(0, displayCount).map((planner: PlannerSummary) => (
              <PersonalPlannerCard
                key={planner.id}
                planner={planner}
                isAuthenticated={isAuthenticated}
                syncEnabled={syncEnabled}
              />
            ))}
          </ResponsiveCardGrid>

          {totalPages > 1 && (
            <div className="mt-6">
              <PlannerListPagination
                currentPage={page}
                totalPages={totalPages}
                onPageChange={onPageChange}
              />
            </div>
          )}
        </>
      )}
    </>
  )
}
